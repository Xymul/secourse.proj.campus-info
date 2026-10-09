<template>
  <div class="page">
    <section class="card">
      <div class="row" style="justify-content: space-between">
        <div class="row">
          <b>校园地图</b>
          <span class="muted small">{{ schoolName }} · 当前视野 {{ nodes.length }} 个信息节点</span>
        </div>
        <div class="row">
          <el-select
            v-model="basemapId"
            size="small"
            style="width: 230px"
            :loading="basemapState.probing"
            @change="switchBasemap"
          >
            <el-option v-for="item in BASEMAPS" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-button size="small" :loading="basemapState.probing" @click="probeBasemaps">检测底图</el-button>
          <el-button size="small" @click="locateMe">📍 定位我</el-button>
          <el-button size="small" :type="nearbyMode ? 'primary' : 'default'" @click="toggleNearby">
            周边 {{ radiusKm }} 公里
          </el-button>
          <el-slider v-model="radiusKm" :min="0.5" :max="10" :step="0.5" style="width: 120px"
                     @change="onRadiusChange" />
          <el-button size="small" @click="reload">刷新</el-button>
        </div>
      </div>

      <div v-if="basemapState.message" class="basemap-hint small"
           :class="{ warn: basemapState.resolvedBy === 'none' }">
        {{ basemapState.message }}
        <el-button v-if="basemapState.resolvedBy === 'auto'" size="small" text type="primary"
                   @click="switchBasemap('osm-de')">切回 OSM 标准地图</el-button>
      </div>

      <div class="row" style="margin: 10px 0">
        <CategoryChips v-model="categoryId" :categories="store.state.categories" @change="() => loadNodes()" />
      </div>

      <div class="map-layout">
        <div class="map-wrapper">
          <div ref="mapEl" class="map-canvas" />
          <div ref="popupEl" class="ol-popup" :class="{ hidden: !popup.visible }">
            <div v-if="popup.node">
              <div class="row" style="justify-content: space-between">
                <b>{{ popup.node.title }}</b>
                <span class="muted small">{{ popup.node.categoryName }}</span>
              </div>
              <div class="muted small" style="margin: 4px 0">
                {{ popup.node.locationName || '未绑定地点' }}
                <template v-if="popup.node.nodeCount > 1"> · 共 {{ popup.node.nodeCount }} 条信息</template>
              </div>
              <div class="row small muted">
                <span>✅ {{ popup.node.verifyTrueCount || 0 }}</span>
                <span>❌ {{ popup.node.verifyFalseCount || 0 }}</span>
                <span>💬 {{ popup.node.commentCount || 0 }}</span>
              </div>
              <div class="row" style="margin-top: 8px">
                <el-button size="small" type="primary" @click="openPost(popup.node.postId)">查看详情</el-button>
                <el-button v-if="popup.node.locationId && popup.node.nodeCount > 1" size="small"
                           @click="loadCluster(popup.node.locationId)">查看同地点信息</el-button>
              </div>
            </div>
          </div>
        </div>

        <aside class="map-side card">
          <div class="card-title">
            <span>{{ sideTitle }}</span>
            <span class="muted small">{{ sideList.length }} 条</span>
          </div>
          <div v-if="nearbyHint" class="muted small" style="margin-bottom: 8px">{{ nearbyHint }}</div>
          <div class="side-list">
            <div v-for="post in sideList" :key="post.postId" class="post-item" @click="openPost(post.postId)">
              <div class="row" style="justify-content: space-between">
                <b>{{ post.title }}</b>
                <span class="muted small">{{ post.distanceKm ? distanceText(post.distanceKm) : '' }}</span>
              </div>
              <div class="muted small">
                {{ post.categoryName }} · {{ post.locationName || '未绑定地点' }} ·
                {{ relativeTime(post.createdAt) }}
              </div>
            </div>
            <div v-if="!sideList.length" class="empty">当前视野暂无信息，试试放大地图或切换分类</div>
          </div>
        </aside>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Map from 'ol/Map'
import View from 'ol/View'
import TileLayer from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import Feature from 'ol/Feature'
import Point from 'ol/geom/Point'
import Overlay from 'ol/Overlay'
import { fromLonLat, toLonLat } from 'ol/proj'
import { Circle as CircleStyle, Fill, Stroke, Style, Text } from 'ol/style'
import CategoryChips from '../components/CategoryChips.vue'
import { postApi } from '../api'
import useStore from '../store'
import { distanceText, relativeTime } from '../utils/format'
import { toDisplayCoordinate, toStorageCoordinate } from '../utils/geo'
import {
  BASEMAPS, basemapState, createBasemapSource, currentBasemap, getBasemapById,
  resolveBasemap, setCurrentBasemap
} from '../utils/basemaps'

const store = useStore()
const router = useRouter()
const route = useRoute()

const mapEl = ref(null)
const popupEl = ref(null)
const nodes = ref([])
const sideList = ref([])
const sideTitle = ref('视野内的信息')
const nearbyHint = ref('')
const categoryId = ref(route.query.categoryId ? Number(route.query.categoryId) : null)
const radiusKm = ref(3)
const nearbyMode = ref(false)
const myPosition = reactive({ latitude: null, longitude: null })
const popup = reactive({ visible: false, node: null })
const basemapId = ref(currentBasemap().id)
const tileErrorCount = ref(0)

let map = null
let nodeSource = null
let mySource = null
let overlay = null
let baseLayer = null
let autoResolveTriggered = false

/** 当前底图坐标系：WGS84（与存储一致）或 GCJ02（国内偏移，需纠偏） */
const activeCrs = () => (getBasemapById(basemapId.value).crs === 'GCJ02' ? 'GCJ02' : 'WGS84')
/** 存储坐标(WGS-84) → 底图显示坐标 */
const toDisplay = (lng, lat) => toDisplayCoordinate(lng, lat, activeCrs())
/** 底图显示坐标 → 存储坐标(WGS-84)，用于地图点击选点后请求接口 */
const fromDisplay = (lng, lat) => toStorageCoordinate(lng, lat, activeCrs())

const schoolName = computed(() => (store.currentSchool.value ? store.currentSchool.value.name : ''))
const colorOf = (code) => {
  const category = store.categoryByCode(code)
  return category ? category.color : '#8fa3b0'
}

const nodeStyle = (feature) => {
  const count = feature.get('nodeCount') || 1
  const color = colorOf(feature.get('categoryCode'))
  const radius = count > 1 ? Math.min(22, 10 + Math.log2(count) * 3) : 7
  return new Style({
    image: new CircleStyle({
      radius,
      fill: new Fill({ color }),
      stroke: new Stroke({ color: '#ffffff', width: 2 })
    }),
    text: count > 1
      ? new Text({
        text: String(count),
        font: 'bold 12px sans-serif',
        fill: new Fill({ color: '#ffffff' })
      })
      : undefined
  })
}

const myStyle = new Style({
  image: new CircleStyle({
    radius: 7,
    fill: new Fill({ color: '#2f7d67' }),
    stroke: new Stroke({ color: '#ffffff', width: 3 })
  })
})

const renderNodes = (list) => {
  if (!nodeSource) return
  nodeSource.clear()
  list.forEach((node) => {
    if (!node.latitude || !node.longitude) return
    const [lng, lat] = toDisplay(node.longitude, node.latitude)
    const feature = new Feature({
      geometry: new Point(fromLonLat([lng, lat])),
      postId: node.postId,
      title: node.title,
      categoryCode: node.categoryCode,
      categoryName: node.categoryName,
      locationId: node.locationId,
      locationName: node.locationName,
      verifyTrueCount: node.verifyTrueCount,
      verifyFalseCount: node.verifyFalseCount,
      commentCount: node.commentCount,
      nodeCount: node.nodeCount || 1
    })
    nodeSource.addFeature(feature)
  })
}

const currentExtent = () => {
  const extent = map.getView().calculateExtent(map.getSize())
  const [minLng, minLat] = toLonLat([extent[0], extent[1]])
  const [maxLng, maxLat] = toLonLat([extent[2], extent[3]])
  return {
    minLat: Math.max(-85, minLat), maxLat: Math.min(85, maxLat),
    minLng: Math.max(-180, minLng), maxLng: Math.min(180, maxLng)
  }
}

const loadNodes = async () => {
  const schoolId = store.state.currentSchoolId
  if (!schoolId || !map) return
  try {
    const list = await postApi.map({
      schoolId,
      ...currentExtent(),
      categoryId: categoryId.value || undefined,
      limit: 800
    })
    nodes.value = list || []
    renderNodes(nodes.value)
    if (!nearbyMode.value) {
      sideTitle.value = '视野内的信息'
      nearbyHint.value = ''
      sideList.value = nodes.value.slice(0, 40)
    }
  } catch (error) {
    ElMessage.error(`地图信息加载失败：${error.message}`)
  }
}

const loadNearby = async (latitude, longitude) => {
  const schoolId = store.state.currentSchoolId
  try {
    const list = await postApi.nearby({
      schoolId, lat: latitude, lng: longitude, radiusKm: radiusKm.value,
      categoryId: categoryId.value || undefined, limit: 50
    })
    sideTitle.value = `周边 ${radiusKm.value} 公里内的信息`
    nearbyHint.value = `搜索中心：${latitude.toFixed(5)}, ${longitude.toFixed(5)}，共 ${list.length} 条`
    sideList.value = list
    renderNodes(list.map((item) => ({ ...item, nodeCount: 1 })))
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const loadCluster = async (locationId) => {
  try {
    const cluster = await postApi.cluster(locationId)
    sideTitle.value = `同地点信息 · ${cluster.locationName}`
    nearbyHint.value = `该地点共有 ${cluster.count} 条有效信息`
    sideList.value = cluster.posts
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const locateMe = () => {
  if (!navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位，请直接在地图上点击位置')
    return
  }
  navigator.geolocation.getCurrentPosition(
    (position) => {
      myPosition.latitude = position.coords.latitude
      myPosition.longitude = position.coords.longitude
      const [lng, lat] = toDisplay(myPosition.longitude, myPosition.latitude)
      mySource.clear()
      mySource.addFeature(new Feature(new Point(fromLonLat([lng, lat]))))
      map.getView().animate({
        center: fromLonLat([lng, lat]),
        zoom: 17,
        duration: 400
      })
      nearbyMode.value = true
      loadNearby(myPosition.latitude, myPosition.longitude)
    },
    () => ElMessage.warning('定位失败，可直接点击地图选择位置'),
    { enableHighAccuracy: true, timeout: 8000 }
  )
}

const toggleNearby = () => {
  nearbyMode.value = !nearbyMode.value
  if (nearbyMode.value) {
    if (myPosition.latitude) {
      loadNearby(myPosition.latitude, myPosition.longitude)
    } else {
      ElMessage.info('请先点击「定位我」，或在地图上点击一个位置')
    }
  } else {
    loadNodes()
  }
}

const onRadiusChange = () => {
  if (nearbyMode.value && myPosition.latitude) {
    loadNearby(myPosition.latitude, myPosition.longitude)
  }
}

const openPost = (postId) => router.push(`/posts/${postId}`)

const reload = () => {
  if (nearbyMode.value && myPosition.latitude) {
    loadNearby(myPosition.latitude, myPosition.longitude)
  } else {
    loadNodes()
  }
}

/** 应用底图：替换瓦片源，并按新坐标系重新渲染信息点（坐标系变化时自动重新居中） */
const applyBasemap = (id, { keepView = false } = {}) => {
  const definition = getBasemapById(id)
  const crsChanged = getBasemapById(basemapId.value).crs !== definition.crs
  basemapId.value = definition.id
  if (baseLayer) {
    baseLayer.setSource(createBasemapSource(definition))
  }
  tileErrorCount.value = 0
  if (map && (!keepView || crsChanged)) {
    const school = store.currentSchool.value
    const center = school
      ? toDisplay(school.centerLongitude, school.centerLatitude)
      : toDisplay(120.2653, 31.4856)
    map.getView().setCenter(fromLonLat(center))
  }
  reload()
}

/** 用户手动切换底图 */
const switchBasemap = (id) => {
  const target = typeof id === 'string' && id ? id : basemapId.value
  setCurrentBasemap(target, 'manual')
  basemapState.resolvedBy = 'manual'
  basemapState.message = `底图：${getBasemapById(target).name}`
  autoResolveTriggered = false
  applyBasemap(target, { keepView: true })
  ElMessage.success('底图已切换')
}

/** 自动探测：当前底图不可达时按候选顺序回退到第一个可用源 */
const autoResolveBasemap = async () => {
  const school = store.currentSchool.value
  const lng = school ? school.centerLongitude : 120.2653
  const lat = school ? school.centerLatitude : 31.4856
  const result = await resolveBasemap(lng, lat, { force: true })
  if (result.id) {
    applyBasemap(result.id, { keepView: true })
  }
  if (result.switched) {
    ElMessage.warning(result.message)
  } else if (!result.id) {
    ElMessage.error(result.message)
  }
}

/** 手动触发一次底图连通性检测 */
const probeBasemaps = async () => {
  const school = store.currentSchool.value
  const lng = school ? school.centerLongitude : 120.2653
  const lat = school ? school.centerLatitude : 31.4856
  const result = await resolveBasemap(lng, lat, { force: true })
  applyBasemap(result.id, { keepView: true })
  ElMessage[result.id ? 'success' : 'error'](result.message || '底图检测完成')
}

onMounted(() => {
  nodeSource = new VectorSource()
  mySource = new VectorSource()

  const school = store.currentSchool.value
  const initialCenter = school
    ? toDisplay(school.centerLongitude, school.centerLatitude)
    : toDisplay(120.2653, 31.4856)

  baseLayer = new TileLayer({ source: createBasemapSource(currentBasemap()) })

  map = new Map({
    target: mapEl.value,
    layers: [
      baseLayer,
      new VectorLayer({ source: nodeSource, style: nodeStyle }),
      new VectorLayer({ source: mySource, style: myStyle })
    ],
    view: new View({
      center: fromLonLat(initialCenter),
      zoom: school ? school.defaultZoom : 16,
      minZoom: 11,
      maxZoom: 19
    })
  })

  // 瓦片加载失败累计到一定次数时，自动探测并切换到可用底图
  baseLayer.getSource().on('tileloaderror', () => {
    tileErrorCount.value += 1
    if (tileErrorCount.value >= 3 && !autoResolveTriggered) {
      autoResolveTriggered = true
      autoResolveBasemap()
    }
  })

  overlay = new Overlay({
    element: popupEl.value,
    autoPan: { animation: { duration: 200 } },
    positioning: 'bottom-center',
    offset: [0, -12]
  })
  map.addOverlay(overlay)

  map.on('singleclick', (event) => {
    let hit = null
    map.forEachFeatureAtPixel(event.pixel, (feature, layer) => {
      if (layer === map.getLayers().item(1)) {
        hit = feature
        return true
      }
      return false
    })
    if (hit) {
      popup.node = {
        postId: hit.get('postId'),
        title: hit.get('title'),
        categoryName: hit.get('categoryName'),
        locationId: hit.get('locationId'),
        locationName: hit.get('locationName'),
        verifyTrueCount: hit.get('verifyTrueCount'),
        verifyFalseCount: hit.get('verifyFalseCount'),
        commentCount: hit.get('commentCount'),
        nodeCount: hit.get('nodeCount')
      }
      popup.visible = true
      overlay.setPosition(event.coordinate)
      return
    }
    popup.visible = false
    const [displayLng, displayLat] = toLonLat(event.coordinate)
    const [lng, lat] = fromDisplay(displayLng, displayLat)
    if (nearbyMode.value) {
      myPosition.latitude = lat
      myPosition.longitude = lng
      mySource.clear()
      mySource.addFeature(new Feature(new Point(event.coordinate)))
      loadNearby(lat, lng)
    }
  })

  map.on('moveend', () => {
    if (!nearbyMode.value) {
      loadNodes()
    }
  })

  loadNodes()
})

watch(() => store.state.currentSchoolId, () => {
  const school = store.currentSchool.value
  if (school && map) {
    const [lng, lat] = toDisplay(school.centerLongitude, school.centerLatitude)
    map.getView().setCenter(fromLonLat([lng, lat]))
    map.getView().setZoom(school.defaultZoom || 16)
    loadNodes()
  }
})

watch(() => route.query.categoryId, (value) => {
  categoryId.value = value ? Number(value) : null
  loadNodes()
})

onBeforeUnmount(() => {
  if (map) {
    map.setTarget(undefined)
    map = null
  }
})


</script>

<style scoped>
.map-layout {
  display: grid;
  grid-template-columns: minmax(0, 2.2fr) minmax(260px, 1fr);
  gap: 14px;
}

.basemap-hint {
  margin: 8px 0;
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--brand-light);
  color: var(--brand);
}

.basemap-hint.warn {
  background: #fdecec;
  color: var(--danger);
}

.map-canvas {
  width: 100%;
  height: 620px;
}

.map-side {
  margin-bottom: 0;
  max-height: 620px;
  overflow: auto;
}

.side-list {
  max-height: 520px;
  overflow: auto;
}

.hidden {
  display: none;
}

@media (max-width: 900px) {
  .map-layout {
    grid-template-columns: 1fr;
  }

  .map-canvas {
    height: 440px;
  }
}
</style>
