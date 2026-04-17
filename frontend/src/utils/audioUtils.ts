export interface RecorderChunk {
  base64: string
  mimeType: string
  /** 仅 PCM 录音时存在，供后端区分 */
  format?: 'pcm'
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i] ?? 0)
  }
  return btoa(binary)
}

/** 将 Float32 转为 S16LE 并重采样到 targetRate。使用低通滤波+线性插值，减少混叠和重复/错字。 */
function float32ToS16LEResampled(
  input: Float32Array,
  sourceRate: number,
  targetRate: number
): ArrayBuffer {
  const ratio = sourceRate / targetRate
  if (ratio <= 0 || ratio > 10) return new ArrayBuffer(0)
  // 降采样前做简单低通，抑制高于 targetRate/2 的频率，减少混叠
  const alpha = 0.4
  const filtered = new Float32Array(input.length)
  let prev = 0
  for (let i = 0; i < input.length; i++) {
    const sample = input[i] ?? 0
    filtered[i] = prev = alpha * sample + (1 - alpha) * prev
  }
  const outLength = Math.floor(filtered.length / ratio)
  const buf = new ArrayBuffer(outLength * 2)
  const view = new DataView(buf)
  const last = filtered.length - 1
  for (let i = 0; i < outLength; i++) {
    const srcIndex = i * ratio
    const idx0 = Math.min(Math.floor(srcIndex), last)
    const idx1 = Math.min(idx0 + 1, last)
    const frac = srcIndex - idx0
    const sample0 = filtered[idx0] ?? 0
    const sample1 = filtered[idx1] ?? 0
    const f = sample0 * (1 - frac) + sample1 * frac
    const s = Math.max(-32768, Math.min(32767, Math.round(f * 32767)))
    view.setInt16(i * 2, s, true)
  }
  return buf
}

const TARGET_SAMPLE_RATE = 16000
type WindowWithWebkitAudioContext = Window & {
  webkitAudioContext?: typeof AudioContext
}
const PCM_CHUNK_BYTES = 640
const RECORDER_AUDIO_CONSTRAINTS: MediaTrackConstraints = {
  channelCount: 1,
  sampleRate: TARGET_SAMPLE_RATE,
  echoCancellation: true,
  noiseSuppression: true,
  autoGainControl: true
}

/**
 * PCM 16kHz 单声道 S16LE 录音器，用于火山实时语音识别。
 * 使用 AudioContext + ScriptProcessor 采集，重采样到 16kHz 并转为 S16LE。
 */
export class PcmRecorder {
  private audioContext: AudioContext | null = null
  private source: MediaStreamAudioSourceNode | null = null
  private processor: ScriptProcessorNode | null = null
  private stream: MediaStream | null = null
  private onChunkCallback: ((chunk: RecorderChunk) => void) | null = null
  private buffer: number[] = []
  private recordedBytes: number[] = []
  private isStarted = false

  async start(onChunk: (chunk: RecorderChunk) => void): Promise<void> {
    if (this.isStarted) return
    this.onChunkCallback = onChunk
    this.buffer = []
    this.recordedBytes = []
    this.stream = await navigator.mediaDevices.getUserMedia({ audio: RECORDER_AUDIO_CONSTRAINTS })
    this.setupAudioGraph()
    this.isStarted = true
  }

  async startBuffered(): Promise<void> {
    if (this.isStarted) return
    this.onChunkCallback = null
    this.buffer = []
    this.recordedBytes = []
    this.stream = await navigator.mediaDevices.getUserMedia({ audio: RECORDER_AUDIO_CONSTRAINTS })
    this.setupAudioGraph()
    this.isStarted = true
  }

  private setupAudioGraph(): void {
    if (!this.stream) return
    const w = window as WindowWithWebkitAudioContext
    const Ctx = w.AudioContext || w.webkitAudioContext
    this.audioContext = new Ctx()
    const sourceRate = this.audioContext.sampleRate
    this.source = this.audioContext.createMediaStreamSource(this.stream)
    // 较小 buffer 更接近 20ms 一帧，有利于 ASR 流式识别
    const bufferSize = 1024
    this.processor = this.audioContext.createScriptProcessor(bufferSize, 1, 1)
    this.processor.onaudioprocess = (e: AudioProcessingEvent) => {
      const input = e.inputBuffer.getChannelData(0)
      const resampled = float32ToS16LEResampled(input, sourceRate, TARGET_SAMPLE_RATE)
      const bytes = new Uint8Array(resampled)
      for (let i = 0; i < bytes.length; i++) {
        const value = bytes[i] ?? 0
        this.buffer.push(value)
        this.recordedBytes.push(value)
      }
      this.flushChunks()
    }
    this.source.connect(this.processor)
    const silentGain = this.audioContext.createGain()
    silentGain.gain.value = 0
    this.processor.connect(silentGain)
    silentGain.connect(this.audioContext.destination)
  }

  private flushChunks(): void {
    if (!this.onChunkCallback) return
    while (this.buffer.length >= PCM_CHUNK_BYTES) {
      const chunk = this.buffer.splice(0, PCM_CHUNK_BYTES)
      const arr = new Uint8Array(chunk)
      this.onChunkCallback({
        base64: arrayBufferToBase64(arr.buffer),
        mimeType: 'audio/raw',
        format: 'pcm'
      })
    }
  }

  stop(): void {
    if (!this.isStarted) return
    if (this.processor) {
      this.processor.disconnect()
      this.processor = null
    }
    if (this.source) this.source.disconnect()
    this.source = null
    if (this.audioContext) this.audioContext.close()
    this.audioContext = null
    this.stream?.getTracks().forEach((t) => t.stop())
    this.stream = null
    const remaining = this.buffer.splice(0, this.buffer.length)
    if (remaining.length > 0 && this.onChunkCallback) {
      const arr = new Uint8Array(remaining)
      this.onChunkCallback({
        base64: arrayBufferToBase64(arr.buffer),
        mimeType: 'audio/raw',
        format: 'pcm'
      })
    }
    this.buffer = []
    this.isStarted = false
  }

  stopAndExport(): RecorderChunk | null {
    if (!this.isStarted) return null
    this.stop()
    if (this.recordedBytes.length === 0) {
      return null
    }
    const arr = new Uint8Array(this.recordedBytes)
    const chunk: RecorderChunk = {
      base64: arrayBufferToBase64(arr.buffer),
      mimeType: 'audio/raw',
      format: 'pcm'
    }
    this.recordedBytes = []
    return chunk
  }

  get started(): boolean {
    return this.isStarted
  }
}

export class AudioRecorder {
  private mediaRecorder: MediaRecorder | null = null
  private stream: MediaStream | null = null
  private onChunkCallback: ((chunk: RecorderChunk) => void) | null = null
  private isStarted = false

  async start(onChunk: (chunk: RecorderChunk) => void): Promise<void> {
    if (this.isStarted) return
    this.onChunkCallback = onChunk
    this.stream = await navigator.mediaDevices.getUserMedia({ audio: RECORDER_AUDIO_CONSTRAINTS })
    const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
      ? 'audio/webm;codecs=opus'
      : 'audio/webm'
    this.mediaRecorder = new MediaRecorder(this.stream, { mimeType })
    this.mediaRecorder.ondataavailable = async (event: BlobEvent) => {
      if (!event.data || event.data.size === 0 || !this.onChunkCallback) return
      const buffer = await event.data.arrayBuffer()
      this.onChunkCallback({
        base64: arrayBufferToBase64(buffer),
        mimeType: event.data.type || mimeType
      })
    }
    this.mediaRecorder.start(200)
    this.isStarted = true
  }

  stop(): void {
    if (!this.mediaRecorder || !this.isStarted) return
    this.mediaRecorder.stop()
    this.stream?.getTracks().forEach((track) => track.stop())
    this.stream = null
    this.mediaRecorder = null
    this.isStarted = false
  }

  get started(): boolean {
    return this.isStarted
  }
}

let audioUnlocked = false

/**
 * 在用户手势（如点击）后调用一次，解除浏览器对后续程序化播放的限制。
 * 建议在「开始面试」「发送」「开始语音」等操作时调用。
 */
export function unlockAudioForPlayback(): void {
  if (audioUnlocked) return
  try {
    const w = window as WindowWithWebkitAudioContext
    const Ctx = w.AudioContext || w.webkitAudioContext
    if (!Ctx) {
      audioUnlocked = true
      return
    }
    const ctx = new Ctx()
    if (ctx.state === 'suspended') {
      ctx.resume()
    }
    audioUnlocked = true
  } catch {
    audioUnlocked = true
  }
}

/**
 * 播放 Base64 音频（后端下发的 interviewer_audio）。
 * 若被浏览器自动播放策略拦截，会抛出，调用方应提示用户「点击页面后重试」。
 */
export async function playBase64Audio(base64: string, mimeType = 'audio/mpeg'): Promise<void> {
  if (!base64) return
  const normalizedMime = mimeType && mimeType.trim() ? mimeType.trim() : 'audio/mpeg'
  const audio = new Audio(`data:${normalizedMime};base64,${base64}`)
  try {
    await audio.play()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    if (/user interaction|interact|gesture|allowed|play\(\)/i.test(msg)) {
      throw new Error('语音播放被浏览器阻止，请先点击页面任意处后再试')
    }
    throw e
  }
}

/**
 * 后端 TTS 不可用时的浏览器本地语音兜底播报。
 * 注意：依赖浏览器的 speechSynthesis 能力与语音包，效果因设备而异。
 */
export function speakTextFallback(text: string): void {
  if (!text?.trim()) return
  if (typeof window === 'undefined' || !('speechSynthesis' in window) || typeof SpeechSynthesisUtterance === 'undefined') {
    return
  }
  try {
    const utter = new SpeechSynthesisUtterance(text.trim())
    utter.lang = 'zh-CN'
    utter.rate = 1
    utter.pitch = 1
    window.speechSynthesis.cancel()
    window.speechSynthesis.speak(utter)
  } catch {
    // ignore fallback errors
  }
}
