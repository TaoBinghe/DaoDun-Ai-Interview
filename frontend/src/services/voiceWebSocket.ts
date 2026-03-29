export interface VoiceServerMessage {
  type: string
  content?: string
  /** 算法题完整题干，与 subtitle 同条消息；供「代码编写」左侧面板 */
  codingProblemContent?: string
  data?: string
  mimeType?: string
  isFinal?: boolean
  emotion?: string
  confidence?: number
  hasFace?: boolean
  capturedAt?: number
  status?: string
}

interface SendAudioPayload {
  sessionId: number
  data: string
  finalChunk?: boolean
  clientTurnId?: string
  /** 音频格式：pcm 用于火山 STT，不传则后端按 webm 处理 */
  format?: 'pcm' | 'webm'
}

interface SendTextPayload {
  sessionId: number
  content: string
  clientTurnId?: string
}

interface SendEmotionFramePayload {
  sessionId: number
  imageBase64: string
  capturedAt?: number
}

export class VoiceWebSocketClient {
  private ws: WebSocket | null = null
  private connectPromise: Promise<void> | null = null

  connect(onMessage: (msg: VoiceServerMessage) => void, onClose?: () => void): Promise<void> {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return Promise.resolve()
    }
    if (this.ws?.readyState === WebSocket.CONNECTING && this.connectPromise) {
      return this.connectPromise
    }

    const token = localStorage.getItem('accessToken')
    if (!token) {
      throw new Error('未登录，无法建立语音通道')
    }
    const url = `ws://localhost:8081/ws/interview?token=${encodeURIComponent(token)}`
    this.ws = new WebSocket(url)
    this.connectPromise = new Promise((resolve, reject) => {
      let settled = false
      const fail = (message: string) => {
        if (settled) return
        settled = true
        this.connectPromise = null
        reject(new Error(message))
      }

      this.ws!.onopen = () => {
        if (settled) return
        settled = true
        resolve()
      }
      this.ws!.onerror = () => {
        fail('语音通道连接失败')
      }
      this.ws!.onclose = () => {
        this.ws = null
        this.connectPromise = null
        onClose?.()
        if (!settled) {
          settled = true
          reject(new Error('语音通道已关闭'))
        }
      }
    })

    this.ws.onmessage = (event) => {
      try {
        const parsed = JSON.parse(event.data) as VoiceServerMessage
        onMessage(parsed)
      } catch {
        // ignore invalid payload
      }
    }
    return this.connectPromise
  }

  disconnect(): void {
    if (!this.ws) return
    this.ws.close()
    this.ws = null
    this.connectPromise = null
  }

  isOpen(): boolean {
    return this.ws?.readyState === WebSocket.OPEN
  }

  sendAudioChunk(payload: SendAudioPayload): void {
    this.send({
      type: 'audio_chunk',
      ...payload
    })
  }

  commitAudio(sessionId: number, clientTurnId?: string, format?: 'pcm' | 'webm'): void {
    this.send({
      type: 'audio_commit',
      sessionId,
      clientTurnId,
      format
    })
  }

  sendTextAnswer(payload: SendTextPayload): void {
    this.send({
      type: 'text_answer',
      ...payload
    })
  }

  /**
   * @param textOnly 文字面试：仅字幕，服务端不合成语音
   */
  sendPlayWelcome(sessionId: number, textOnly?: boolean): void {
    this.send({
      type: 'play_welcome',
      sessionId,
      ...(textOnly ? { textOnly: true } : {})
    })
  }

  sendEmotionFrame(payload: SendEmotionFramePayload): void {
    this.send({
      type: 'emotion_frame',
      ...payload
    })
  }

  private send(payload: object): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error('语音通道未连接')
    }
    this.ws.send(JSON.stringify(payload))
  }
}
