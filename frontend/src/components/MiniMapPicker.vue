<template>
  <div class="mini-map-wrap">
    <div ref="mapEl" class="mini-map" />
    <div class="mini-hint small muted">
      点击地图可选择信息所在位置（当前：
      <b v-if="modelValue.latitude">{{ modelValue.latitude.toFixed(5) }}, {{ modelValue.longitude.toFixed(5) }}</b>
      <b v-else>未选择</b>）
    </div>
    <div class="row" style="margin-top: 6px">
      <span v-for="location in locations.slice(0, 8)" :key="location.locationId" class="chip"
            @click="pickLocation(location)">📍 {{ location.name }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Map from 'ol/Map'
import View from 'ol/View'
import TileLayer from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import Feature from 'ol/Feature'
import Point from 'ol/geom/Point'
import { fromLonLat, toLonLat } from 'ol/proj'
import { Circle as CircleStyle, Fill, Stroke, Style, Text } from 'ol/style'
import { toDisplayCoordinate, toStorageCoordinate } from '../utils/geo'
import { basemapState, createBasemapSource, currentBasemap } from '../utils/basemaps'

const props = defineProps({
  modelValue: { type: Object, default: () => ({ latitude: null, longitude: null }) },
  center: { type: Array, default: () => [120.2653, 31.4856] },
  locations: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])
const mapEl = ref(null)
let map = null
let baseLayer = null
let markerSource = null
let locationSource = null

/** 与地图页共享同一底图选择；高德等 GCJ-02 底图会自动做坐标纠偏 */
const crs = computed(() => (currentBasemap().crs === 'GCJ02' ? 'GCJ02' : 'WGS84'))
const toDisplay = (lng, lat) => toDisplayCoordinate(lng, lat, crs.value)
const fromDisplay = (lng, lat) => toStorageCoordinate(lng, lat, crs.value)

const markerStyle = new Style({
  image: new CircleStyle({ radius: 8, fill: new Fill({ color: '#d9534f' }), stroke: new Stroke({ color: '#fff', width: 2 }) })
})

const locationStyle = new Style({
  image: new CircleStyle({ radius: 6, fill: new Fill({ color: '#2f7d67' }), stroke: new Stroke({ color: '#fff', width: 1.5 }) }),
  text: new Text({ text: '', offsetY: -16, font: '11px sans-serif', fill: new Fill({ color: '#22313a' }) })
})

const renderLocations = () => {
  if (!locationSource) return
  locationSource.clear()
  props.locations.forEach((location) => {
    if (!location.latitude || !location.longitude) return
    const [lng, lat] = toDisplay(location.longitude, location.latitude)
    const feature = new Feature({
      geometry: new Point(fromLonLat([lng, lat])),
      name: location.name
    })
    locationSource.addFeature(feature)
  })
}

onMounted(() => {
  markerSource = new VectorSource()
  locationSource = new VectorSource({ wrapX: false })

  baseLayer = new TileLayer({ source: createBasemapSource(currentBasemap()) })
  const [centerLng, centerLat] = toDisplay(props.center[0], props.center[1])

  map = new Map({
    target: mapEl.value,
    layers: [
      baseLayer,
      new VectorLayer({
        source: locationSource,
        style: (feature) => new Style({
          image: locationStyle.getImage(),
          text: new Text({
            text: feature.get('name'),
            offsetY: -16,
            font: '11px sans-serif',
            fill: new Fill({ color: '#22313a' }),
            stroke: new Stroke({ color: 'rgba(255,255,255,0.85)', width: 3 })
          })
        })
      }),
      new VectorLayer({ source: markerSource, style: markerStyle })
    ],
    view: new View({
      center: fromLonLat([centerLng, centerLat]),
      zoom: 16,
      minZoom: 12,
      maxZoom: 19
    })
  })

  renderLocations()
  syncMarker()

  map.on('singleclick', (event) => {
    const [displayLng, displayLat] = toLonLat(event.coordinate)
    const [lng, lat] = fromDisplay(displayLng, displayLat)
    emit('update:modelValue', {
      latitude: Math.round(lat * 1000000) / 1000000,
      longitude: Math.round(lng * 1000000) / 1000000
    })
  })
})

const syncMarker = () => {
  if (!markerSource) return
  markerSource.clear()
  const { latitude, longitude } = props.modelValue || {}
  if (latitude && longitude) {
    const [lng, lat] = toDisplay(longitude, latitude)
    markerSource.addFeature(new Feature(new Point(fromLonLat([lng, lat]))))
  }
}

const pickLocation = (location) => {
  emit('update:modelValue', { latitude: location.latitude, longitude: location.longitude })
  if (map) {
    const [lng, lat] = toDisplay(location.longitude, location.latitude)
    map.getView().animate({ center: fromLonLat([lng, lat]), zoom: 17, duration: 300 })
  }
}

watch(() => props.modelValue, syncMarker, { deep: true })
watch(() => props.locations, renderLocations, { deep: true })
watch(() => props.center, (value) => {
  if (map && value) {
    const [lng, lat] = toDisplay(value[0], value[1])
    map.getView().setCenter(fromLonLat([lng, lat]))
  }
})

// 底图切换时同步替换瓦片源，并按新坐标系重绘
watch(() => basemapState.currentId, () => {
  if (!map) return
  baseLayer.setSource(createBasemapSource(currentBasemap()))
  renderLocations()
  syncMarker()
})

onBeforeUnmount(() => {
  if (map) {
    map.setTarget(undefined)
    map = null
  }
})
</script>

<style scoped>
.mini-map {
  width: 100%;
  height: 260px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border);
}

.mini-hint {
  margin-top: 6px;
}
</style>
