import { reactive } from 'vue'
import XYZ from 'ol/source/XYZ'
import { lonLatToTile, toDisplayCoordinate } from './geo'

/**
 * 底图源管理：多底图可切换 + 启动自动探测可用源。
 *
 * 背景：`tile.openstreetmap.org` 等部分国外瓦片服务在国内网络下会连接超时，
 * 现象是「能看到信息点但看不到底图」。这里内置多个可用源并在启动时探测，
 * 自动选中第一个可达的源，用户也可手动切换。
 *
 * crs 说明：WGS84 表示底图与系统存储坐标一致；GCJ02 表示国内偏移坐标，
 * 需要按 geo.js 转换后进行纠偏显示，保证信息点与底图对齐。
 */

const STORAGE_KEY = 'campus_basemap'

export const BASEMAPS = [
  {
    id: 'osm-de',
    name: 'OSM 标准地图（德国镜像）',
    crs: 'WGS84',
    attribution: '© OpenStreetMap contributors',
    maxZoom: 19,
    urls: ['https://tile.openstreetmap.de/{z}/{x}/{y}.png']
  },
  {
    id: 'gaode-vector',
    name: '高德地图（中文标注，自动纠偏）',
    crs: 'GCJ02',
    attribution: '© 高德地图',
    maxZoom: 18,
    urls: [
      'https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
      'https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
      'https://webrd03.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
      'https://webrd04.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'
    ]
  },
  {
    id: 'gaode-satellite',
    name: '高德卫星影像（自动纠偏）',
    crs: 'GCJ02',
    attribution: '© 高德地图',
    maxZoom: 18,
    urls: [
      'https://webst01.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
      'https://webst02.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
      'https://webst03.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
      'https://webst04.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}'
    ]
  },
  {
    id: 'esri-imagery',
    name: 'ESRI 全球卫星影像',
    crs: 'WGS84',
    attribution: '© Esri, Maxar, Earthstar Geographics',
    maxZoom: 19,
    urls: ['https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}']
  },
  {
    id: 'esri-street',
    name: 'ESRI 街道地图',
    crs: 'WGS84',
    attribution: '© Esri',
    maxZoom: 19,
    urls: ['https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}']
  },
  {
    id: 'osm-hot',
    name: 'OSM HOT 镜像',
    crs: 'WGS84',
    attribution: '© OpenStreetMap contributors, Humanitarian OpenStreetMap Team',
    maxZoom: 19,
    urls: [
      'https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png',
      'https://b.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png'
    ]
  },
  {
    id: 'osm-official',
    name: 'OSM 官方（需国外网络）',
    crs: 'WGS84',
    attribution: '© OpenStreetMap contributors',
    maxZoom: 19,
    urls: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png']
  },
  {
    id: 'carto-light',
    name: 'Carto 简洁风格（需国外网络）',
    crs: 'WGS84',
    attribution: '© OpenStreetMap contributors, © CARTO',
    maxZoom: 19,
    urls: [
      'https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png',
      'https://b.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png'
    ]
  }
]

/** 自动探测顺序：优先选择「国内网络可达 + 与数据坐标系一致」的源 */
export const PROBE_ORDER = ['osm-de', 'gaode-vector', 'esri-street', 'esri-imagery', 'osm-hot',
  'osm-official', 'carto-light']

export const basemapState = reactive({
  currentId: localStorage.getItem(STORAGE_KEY) || '',
  probing: false,
  /** 底图来源：manual（用户手选） / auto（自动探测） / none（均不可达） */
  resolvedBy: '',
  message: ''
})

export const getBasemapById = (id) => BASEMAPS.find((item) => item.id === id) || BASEMAPS[0]

export const currentBasemap = () => getBasemapById(basemapState.currentId || PROBE_ORDER[0])

export const setCurrentBasemap = (id, resolvedBy = 'manual') => {
  basemapState.currentId = id
  basemapState.resolvedBy = resolvedBy
  localStorage.setItem(STORAGE_KEY, id)
}

/** 创建 OpenLayers 瓦片源 */
export const createBasemapSource = (definition) => new XYZ({
  urls: definition.urls,
  attributions: definition.attribution,
  maxZoom: definition.maxZoom || 19,
  wrapX: true,
  crossOrigin: 'anonymous'
})

/** 用一张真实瓦片探测底图是否可达（默认 4 秒超时） */
export const probeBasemap = (definition, lng = 120.2653, lat = 31.4856, timeout = 4000) => {
  const probeZoom = 15
  // 按底图坐标系换算坐标，确保请求到有数据的瓦片
  const [displayLng, displayLat] = toDisplayCoordinate(lng, lat, definition.crs)
  const tile = lonLatToTile(displayLng, displayLat, probeZoom)
  const url = definition.urls[0]
    .replace('{z}', String(tile.z))
    .replace('{x}', String(tile.x))
    .replace('{y}', String(tile.y))

  return new Promise((resolve) => {
    const image = new Image()
    let settled = false
    const finish = (ok) => {
      if (settled) return
      settled = true
      image.onload = null
      image.onerror = null
      resolve(ok)
    }
    const timer = setTimeout(() => finish(false), timeout)
    image.onload = () => {
      clearTimeout(timer)
      finish(true)
    }
    image.onerror = () => {
      clearTimeout(timer)
      finish(false)
    }
    image.src = url
  })
}

/**
 * 解析出可用底图：优先保留用户上次的手动选择；不可达时按 PROBE_ORDER 自动回退。
 * @returns {Promise<{id: string, message: string, switched: boolean}>}
 */
export const resolveBasemap = async (lng, lat, { force = false } = {}) => {
  if (basemapState.probing) {
    return { id: basemapState.currentId, message: basemapState.message, switched: false }
  }
  basemapState.probing = true
  try {
    const stored = basemapState.currentId
    if (stored && !force) {
      const definition = getBasemapById(stored)
      const ok = await probeBasemap(definition, lng, lat)
      if (ok) {
        basemapState.resolvedBy = 'manual'
        basemapState.message = `底图：${definition.name}`
        return { id: stored, message: basemapState.message, switched: false }
      }
    }
    for (const id of PROBE_ORDER) {
      const definition = getBasemapById(id)
      // eslint-disable-next-line no-await-in-loop
      const ok = await probeBasemap(definition, lng, lat)
      if (ok) {
        const switched = stored !== id
        setCurrentBasemap(id, 'auto')
        basemapState.message = `底图：${definition.name}`
          + (switched && stored ? '（原底图不可达，已自动切换）' : '')
        return { id, message: basemapState.message, switched }
      }
    }
    basemapState.resolvedBy = 'none'
    basemapState.message = '未能连接到任何底图服务，可在底图下拉框中手动切换；信息点、搜索与发布功能不受影响'
    return { id: basemapState.currentId, message: basemapState.message, switched: false }
  } finally {
    basemapState.probing = false
  }
}

