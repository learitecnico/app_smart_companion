import WebSocket from 'ws';
import { EventEmitter } from 'events';
import { Logger } from '../utils/Logger';

const logger = Logger.getInstance();

export interface RealtimeEvent {
  event_id?: string;
  type: string;
  [key: string]: any;
}

export interface SessionConfig {
  modalities: ('text' | 'audio')[];
  instructions: string;
  voice: 'alloy' | 'echo' | 'fable' | 'onyx' | 'nova' | 'shimmer';
  input_audio_format: 'pcm16' | 'g711_ulaw' | 'g711_alaw';
  output_audio_format: 'pcm16' | 'g711_ulaw' | 'g711_alaw';
  input_audio_transcription?: {
    model: 'whisper-1';
  } | null;
  turn_detection: {
    type: 'server_vad' | 'none';
    threshold?: number;
    prefix_padding_ms?: number;
    silence_duration_ms?: number;
  };
  tools?: any[];
  tool_choice?: 'auto' | 'none' | 'required';
  temperature?: number;
}

export class RealtimeClient extends EventEmitter {
  private ws: WebSocket | null = null;
  private isConnected = false;
  private reconnectAttempts = 0;
  private readonly maxReconnectAttempts = 5;
  private readonly reconnectDelay = 5000;
  private sessionConfig: SessionConfig;

  // Sales Coach: Portuguese system prompt for smart glasses
  private getPortugueseSystemPrompt(): string {
    return `Você é um COACH DE VENDAS INTELIGENTE integrado aos óculos inteligentes Vuzix M400.

MISSÃO: Analisar reuniões de vendas em tempo real e fornecer dicas estratégicas baseadas em SPIN Selling.

IDIOMA: Responda SEMPRE em português brasileiro, natural e conversacional.

REGRA CRÍTICA DE FILTRAGEM:
- SÓ responda quando identificar uma OPORTUNIDADE ESPECÍFICA de vendas
- NÃO faça comentários gerais ou observações desnecessárias
- NÃO repita informações óbvias sobre a conversa
- SEMPRE use a ferramenta display_on_hud para dicas relevantes
- Se não há dica estratégica, permaneça SILENCIOSO

CONTEXTO DE USO:
- O usuário está em uma reunião de vendas ou negociação
- Você analisa continuamente a conversa em áudio
- Você fornece APENAS dicas discretas e ACIONÁVEIS no HUD
- Foque em melhorar a performance de vendas

METODOLOGIA SPIN SELLING:
- SITUATION: Perguntas sobre situação atual do cliente
- PROBLEM: Identificar problemas e dores do cliente  
- IMPLICATION: Explorar consequências dos problemas
- NEED-PAYOFF: Destacar benefícios da solução

ESTILO DE COMUNICAÇÃO:
- MÁXIMO 2-3 frases por dica
- Linguagem sussurrada e discreta
- Ações específicas e imediatas
- Timing preciso para intervenções

EXEMPLOS DE QUANDO RESPONDER:
✅ Cliente menciona problema específico → "Pergunte sobre o impacto financeiro"
✅ Cliente demonstra interesse → "Feche com proposta específica agora"
✅ Momento de objeção → "Mencione case de sucesso similar"

EXEMPLOS DE QUANDO NÃO RESPONDER:
❌ Conversa casual sem oportunidade de venda
❌ Cliente apenas fazendo perguntas exploratórias
❌ Discussões administrativas ou logísticas

Seja estratégico, discreto e focado APENAS em resultados de vendas.`;
  }

  constructor(private apiKey: string) {
    super();
    
    // ElatoAI inspired: Portuguese optimized session config
    this.sessionConfig = {
      modalities: ['text', 'audio'],  // Both modalities for flexibility
      instructions: this.getPortugueseSystemPrompt(),
      voice: 'alloy',  // Best voice for Portuguese
      input_audio_format: 'pcm16',
      output_audio_format: 'pcm16',
      input_audio_transcription: {
        model: 'whisper-1'
      },
      turn_detection: {
        type: 'server_vad',
        threshold: 0.4,              // ElatoAI: More sensitive for faster detection
        prefix_padding_ms: 200,      // ElatoAI: Reduced padding for agile response
        silence_duration_ms: 400,    // ElatoAI: 33% faster - sales conversations need speed
      },
      // VideoSDK smart glasses optimizations (corrected for API limits)
      temperature: 0.6,  // Minimum allowed by OpenAI Realtime API (was 0.3 - too low)
      tools: [
        {
          type: 'function',
          name: 'display_on_hud',
          description: 'Display sales coaching tip on smart glasses HUD - ALWAYS use this tool for coaching tips',
          parameters: {
            type: 'object',
            properties: {
              text: {
                type: 'string',
                description: 'Sales coaching tip to display on HUD (max 30 words, discrete language)'
              },
              priority: {
                type: 'string',
                enum: ['low', 'medium', 'high', 'urgent'],
                description: 'Coaching tip priority (urgent for closing opportunities)'
              },
              spin_type: {
                type: 'string',
                enum: ['situation', 'problem', 'implication', 'need_payoff', 'closing'],
                description: 'Type of SPIN selling technique being suggested'
              }
            },
            required: ['text', 'spin_type']
          }
        }
      ],
      tool_choice: 'auto'  // CRITICAL: 'auto' preserves existing text_complete flow, 'required' would break it
    };
  }

  async connect(): Promise<void> {
    if (this.isConnected) {
      logger.warn('RealtimeClient already connected');
      return;
    }

    try {
      const url = 'wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-10-01';
      
      this.ws = new WebSocket(url, {
        headers: {
          'Authorization': `Bearer ${this.apiKey}`,
          'OpenAI-Beta': 'realtime=v1'
        }
      });

      this.setupWebSocketEvents();
      
      await this.waitForConnection();
      await this.initializeSession();

    } catch (error) {
      logger.error('Failed to connect to OpenAI Realtime API', { error });
      throw error;
    }
  }

  private setupWebSocketEvents(): void {
    if (!this.ws) return;

    this.ws.on('open', () => {
      logger.info('Connected to OpenAI Realtime API');
      this.isConnected = true;
      this.reconnectAttempts = 0;
      this.emit('connected');
    });

    this.ws.on('message', (data: Buffer) => {
      try {
        const event: RealtimeEvent = JSON.parse(data.toString());
        logger.debug('Received event from OpenAI', { type: event.type });
        this.handleEvent(event);
      } catch (error) {
        logger.error('Failed to parse OpenAI event', { error });
      }
    });

    this.ws.on('close', (code: number, reason: Buffer) => {
      logger.info('Disconnected from OpenAI Realtime API', { code, reason: reason.toString() });
      this.isConnected = false;
      this.emit('disconnected');
      this.scheduleReconnect();
    });

    this.ws.on('error', (error: Error) => {
      logger.error('OpenAI WebSocket error', { error });
      this.emit('error', error);
    });
  }

  private async waitForConnection(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (!this.ws) {
        reject(new Error('WebSocket not initialized'));
        return;
      }

      const timeout = setTimeout(() => {
        reject(new Error('Connection timeout'));
      }, 10000);

      this.ws.once('open', () => {
        clearTimeout(timeout);
        resolve();
      });

      this.ws.once('error', (error) => {
        clearTimeout(timeout);
        reject(error);
      });
    });
  }

  private async initializeSession(): Promise<void> {
    const sessionUpdateEvent: RealtimeEvent = {
      event_id: this.generateEventId(),
      type: 'session.update',
      session: this.sessionConfig
    };

    this.sendEvent(sessionUpdateEvent);
    logger.info('Session initialized with configuration', { 
      modalities: this.sessionConfig.modalities,
      voice: this.sessionConfig.voice 
    });
  }

  private handleEvent(event: RealtimeEvent): void {
    this.emit('event', event);

    switch (event.type) {
      case 'session.created':
        logger.info('Session created', { session_id: event.session?.id });
        break;

      case 'session.updated':
        logger.info('Session updated');
        break;

      case 'conversation.item.created':
        logger.debug('Conversation item created', { item_id: event.item?.id });
        break;

      case 'response.created':
        logger.debug('Response created', { response_id: event.response?.id });
        break;


      case 'response.content_part.added':
        if (event.part?.type === 'text') {
          this.emit('text_delta', event.part.text);
        } else if (event.part?.type === 'audio') {
          this.emit('audio_delta', event.part);
        }
        break;

      case 'response.content_part.done':
        logger.info('🎯 RESPONSE CONTENT PART DONE - CRITICAL EVENT!', { 
          partType: event.part?.type,
          textContent: event.part?.text?.substring(0, 100) + '...' || 'No text',
          fullEvent: JSON.stringify(event).substring(0, 300) + '...'
        });
        
        if (event.part?.type === 'text') {
          logger.info('🎯 TEXT PART detected - forwarding to text_complete');
          this.emit('text_complete', event.part.text);
        } else if (event.part?.type === 'audio') {
          logger.info('🎯 AUDIO PART detected - forwarding to audio_complete');
          this.emit('audio_complete', event.part);
        } else {
          logger.warn('🚨 UNKNOWN PART TYPE in response.content_part.done', { 
            partType: event.part?.type,
            partKeys: Object.keys(event.part || {})
          });
        }
        break;

      case 'response.done':
        logger.debug('Response completed', { response_id: event.response?.id });
        this.emit('response_complete', event.response);
        break;

      // VideoSDK pattern: Handle tool calls (corrected events)
      case 'response.output_item.added':
        if (event.item?.type === 'function_call') {
          logger.debug('Function call started', { 
            call_id: event.item.call_id,
            name: event.item.name 
          });
        }
        logger.debug('Response output item added', { item_id: event.item?.id });
        break;

      case 'response.output_item.done':
        if (event.item?.type === 'function_call') {
          logger.info('🎯 FUNCTION CALL completed', { 
            call_id: event.item.call_id,
            name: event.item.name,
            arguments: event.item.arguments,
            status: event.item.status
          });
          this.handleFunctionCall(event.item);
        }
        break;

      case 'input_audio_buffer.speech_started':
        logger.debug('Speech started');
        this.emit('speech_started');
        break;

      case 'input_audio_buffer.speech_stopped':
        logger.debug('Speech stopped');
        this.emit('speech_stopped');
        break;

      case 'input_audio_buffer.cleared':
        logger.debug('Audio buffer cleared');
        this.emit('audio_buffer_cleared');
        break;

      case 'conversation.item.input_audio_transcription.completed':
        logger.debug('Audio transcription completed', { 
          item_id: event.item_id,
          transcript: event.transcript 
        });
        this.emit('transcription_complete', event.transcript);
        break;

      case 'conversation.item.input_audio_transcription.failed':
        logger.warn('Audio transcription failed', { 
          item_id: event.item_id,
          error: event.error 
        });
        this.emit('transcription_error', event.error);
        break;

      case 'response.audio_transcript.delta':
        logger.debug('Audio transcript delta received');
        this.emit('audio_transcript_delta', event.delta);
        break;

      case 'response.audio_transcript.done':
        logger.info('🎯 AUDIO TRANSCRIPT COMPLETED - contains TEXT from audio response!', { 
          transcript: event.transcript?.substring(0, 100) + '...' || 'No transcript',
          fullEvent: JSON.stringify(event).substring(0, 500) + '...'  // Debug: show full event structure
        });
        
        // This contains the TEXT version of the audio response!
        if (event.transcript) {
          this.emit('text_complete', event.transcript);  // Emit as text_complete for compatibility
          logger.info('🎯 Audio transcript forwarded as text_complete for HUD display');
        } else {
          logger.warn('🚨 AUDIO TRANSCRIPT DONE but no transcript property found!', { 
            eventKeys: Object.keys(event),
            eventType: event.type
          });
        }
        
        this.emit('audio_transcript_complete', event.transcript);
        break;

      case 'error':
        this.handleApiError(event.error);
        break;

      default:
        logger.debug('Unhandled event type', { type: event.type });
    }
  }

  // Sales Coach: Handle function calls (display_on_hud tool with SPIN context)
  private handleFunctionCall(item: any): void {
    const { call_id, name, arguments: args } = item;
    
    if (name === 'display_on_hud') {
      try {
        const parsedArgs = JSON.parse(args);
        const { text, priority = 'medium', spin_type = 'general' } = parsedArgs;
        
        logger.info('💼 SALES COACHING TIP tool called', { 
          text: text.substring(0, 30) + '...',
          priority,
          spin_type,
          call_id 
        });
        
        // Emit to OpenAIBridge for forwarding to WebRTC with sales context
        this.emit('hud_display_request', { 
          text, 
          priority, 
          spin_type,
          call_id,
          context: 'sales_coaching'
        });
        
        // Send function call result back to OpenAI
        this.sendFunctionCallResult(call_id, {
          success: true,
          message: `Dica de vendas exibida no HUD: "${text.substring(0, 25)}..." (${spin_type})`
        });
        
      } catch (error) {
        logger.error('Failed to handle sales coaching display', { error });
        this.sendFunctionCallResult(call_id, {
          success: false,
          error: 'Falha ao exibir dica de vendas no HUD'
        });
      }
    } else {
      logger.warn('Unknown function call', { name, call_id });
      this.sendFunctionCallResult(call_id, {
        success: false,
        error: `Função desconhecida: ${name}`
      });
    }
  }

  private sendFunctionCallResult(call_id: string, result: any): void {
    // Send function call result back to OpenAI
    const functionResultEvent: RealtimeEvent = {
      event_id: this.generateEventId(),
      type: 'conversation.item.create',
      item: {
        type: 'function_call_output',
        call_id: call_id,
        output: JSON.stringify(result)
      }
    };
    
    this.sendEvent(functionResultEvent);
    
    // Create a new response to continue the conversation
    const responseEvent: RealtimeEvent = {
      event_id: this.generateEventId(),
      type: 'response.create'
    };
    
    this.sendEvent(responseEvent);
    
    logger.debug('Function call result sent and response requested', { call_id, result });
  }

  sendAudio(audioBuffer: Buffer): void {
    if (!this.isConnected) {
      logger.warn('Cannot send audio: not connected');
      return;
    }

    // Convert audio buffer to base64
    const audioBase64 = audioBuffer.toString('base64');

    const event: RealtimeEvent = {
      event_id: this.generateEventId(),
      type: 'input_audio_buffer.append',
      audio: audioBase64
    };

    this.sendEvent(event);
    logger.debug('Audio sent to OpenAI', { size: audioBuffer.length });
  }

  commitAudio(): void {
    const event: RealtimeEvent = {
      event_id: this.generateEventId(),
      type: 'input_audio_buffer.commit'
    };

    this.sendEvent(event);
    logger.debug('Audio buffer committed');
  }

  sendText(text: string): void {
    const event: RealtimeEvent = {
      event_id: this.generateEventId(),
      type: 'conversation.item.create',
      item: {
        type: 'message',
        role: 'user',
        content: [
          {
            type: 'input_text',
            text: text
          }
        ]
      }
    };

    this.sendEvent(event);
    this.createResponse();
    logger.debug('Text sent to OpenAI', { text });
  }

  private createResponse(): void {
    const event: RealtimeEvent = {
      event_id: this.generateEventId(),
      type: 'response.create',
      response: {
        modalities: this.sessionConfig.modalities,
        instructions: null
      }
    };

    this.sendEvent(event);
  }

  updateInstructions(instructions: string): void {
    this.sessionConfig.instructions = instructions;
    
    const event: RealtimeEvent = {
      event_id: this.generateEventId(),
      type: 'session.update',
      session: {
        instructions: instructions
      }
    };

    this.sendEvent(event);
    logger.info('Instructions updated', { 
      preview: instructions.substring(0, 100) + '...'
    });
  }

  updateSessionConfig(config: Partial<SessionConfig>): void {
    this.sessionConfig = { ...this.sessionConfig, ...config };
    
    const event: RealtimeEvent = {
      event_id: this.generateEventId(),
      type: 'session.update',
      session: this.sessionConfig
    };

    this.sendEvent(event);
    logger.info('Session configuration updated', { config });
  }

  private sendEvent(event: RealtimeEvent): void {
    if (!this.ws || !this.isConnected) {
      logger.warn('Cannot send event: not connected');
      return;
    }

    try {
      this.ws.send(JSON.stringify(event));
      logger.debug('Event sent to OpenAI', { type: event.type });
    } catch (error) {
      logger.error('Failed to send event to OpenAI', { error, type: event.type });
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      logger.error('Max reconnection attempts reached');
      this.emit('max_reconnect_attempts');
      return;
    }

    this.reconnectAttempts++;
    logger.info('Scheduling reconnection attempt', { 
      attempt: this.reconnectAttempts,
      delay: this.reconnectDelay 
    });

    setTimeout(() => {
      if (!this.isConnected) {
        this.connect().catch(error => {
          logger.error('Reconnection failed', { error });
        });
      }
    }, this.reconnectDelay * this.reconnectAttempts);
  }

  private handleApiError(error: any): void {
    const errorType = error?.type || 'unknown_error';
    const errorCode = error?.code || 'unknown_code';
    
    logger.error('OpenAI API error', { 
      type: errorType,
      code: errorCode,
      message: error?.message,
      param: error?.param,
      event_id: error?.event_id
    });

    // Emit specific error events based on type
    switch (errorType) {
      case 'invalid_request_error':
        this.emit('invalid_request', error);
        break;
      case 'authentication_error':
        this.emit('authentication_error', error);
        break;
      case 'permission_error':
        this.emit('permission_error', error);
        break;
      case 'not_found_error':
        this.emit('not_found_error', error);
        break;
      case 'rate_limit_error':
        this.emit('rate_limit_error', error);
        break;
      case 'api_error':
        this.emit('api_error', error);
        break;
      case 'overloaded_error':
        this.emit('overloaded_error', error);
        break;
      case 'server_error':
        this.emit('server_error', error);
        break;
      default:
        this.emit('unknown_error', error);
    }
    
    // Also emit generic error event
    this.emit('api_error', error);
  }

  private generateEventId(): string {
    return `event_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  }

  disconnect(): void {
    if (this.ws) {
      this.ws.close(1000, 'Client disconnecting');
      this.ws = null;
    }
    this.isConnected = false;
    logger.info('RealtimeClient disconnected');
  }

  isReady(): boolean {
    return this.isConnected;
  }

  getSessionConfig(): SessionConfig {
    return { ...this.sessionConfig };
  }
}