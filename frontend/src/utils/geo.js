/**
 * 坐标系转换工具：WGS-84（GPS / OSM / 后端存储）↔ GCJ-02（高德 / 腾讯等国内地图）。
 *
 * 系统内所有信息点坐标统一以 WGS-84 存储；当用户选择高德等 GCJ-02 底图时，
 * 显示前转换为 GCJ-02，用户在地图上点击选点时再转回 WGS-84 提交给后端，
 * 从而保证「底图与信息点对齐」且「入库坐标始终是标准 GPS 坐标」。
 */

const A = 6378245.0
const EE = 0.00669342162296594323

/** 是否在中国境外（境外不做纠偏） */
export const outOfChina = (lng, lat) =>
  lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271

const transformLat = (x, y) => {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin((y / 3.0) * Math.PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((y / 12.0) * Math.PI) + 320 * Math.sin((y * Math.PI) / 30.0)) * 2.0) / 3.0
  return ret
}

const transformLng = (x, y) => {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin((x / 3.0) * Math.PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((x / 12.0) * Math.PI) + 300.0 * Math.sin((x / 30.0) * Math.PI)) * 2.0) / 3.0
  return ret
}

/** WGS-84 → GCJ-02，返回 [lng, lat] */
export const wgs84ToGcj02 = (lng, lat) => {
  if (outOfChina(lng, lat)) {
    return [lng, lat]
  }
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = (lat / 180.0) * Math.PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * Math.PI)
  dLng = (dLng * 180.0) / ((A / sqrtMagic) * Math.cos(radLat) * Math.PI)
  return [lng + dLng, lat + dLat]
}

/** GCJ-02 → WGS-84（数值迭代反解，精度优于 1e-6 度） */
export const gcj02ToWgs84 = (lng, lat) => {
  if (outOfChina(lng, lat)) {
    return [lng, lat]
  }
  let wgsLng = lng
  let wgsLat = lat
  for (let i = 0; i < 6; i += 1) {
    const [gcjLng, gcjLat] = wgs84ToGcj02(wgsLng, wgsLat)
    const dLng = gcjLng - lng
    const dLat = gcjLat - lat
    if (Math.abs(dLng) < 1e-7 && Math.abs(dLat) < 1e-7) {
      break
    }
    wgsLng -= dLng
    wgsLat -= dLat
  }
  return [wgsLng, wgsLat]
}

/**
 * 按底图坐标系把「存储坐标(WGS-84)」转为「显示坐标」
 * @param {'WGS84'|'GCJ02'} crs 底图坐标系
 */
export const toDisplayCoordinate = (lng, lat, crs) =>
  (crs === 'GCJ02' ? wgs84ToGcj02(lng, lat) : [lng, lat])

/** 按底图坐标系把「用户点击得到的显示坐标」转回存储坐标(WGS-84) */
export const toStorageCoordinate = (lng, lat, crs) =>
  (crs === 'GCJ02' ? gcj02ToWgs84(lng, lat) : [lng, lat])

/** 经纬度 → XYZ 瓦片编号（用于底图连通性探测） */
export const lonLatToTile = (lng, lat, zoom) => {
  const n = 2 ** zoom
  const x = Math.floor(((lng + 180) / 360) * n)
  const latRad = (lat * Math.PI) / 180
  const y = Math.floor(((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2) * n)
  return { x, y, z: zoom }
}
