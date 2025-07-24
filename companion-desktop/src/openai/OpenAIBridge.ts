import OpenAI from 'openai';
import { z } from 'zod';
import { RealtimeClient, SessionConfig } from './RealtimeClient';
import { Logger } from '../utils/Logger';

const logger = Logger.getInstance();

interface VisionAnalysis {
  description: string;
  objects: string[];
  actions?: string[];
  context?: string;
}

export class OpenAIBridge {
  private realtimeClient: RealtimeClient;
  private openaiClient: OpenAI;
  private isConnectedFlag = false;
  private onTextResponseCallback?: (text: string) => void;
  private onAudioResponseCallback?: (audioData: Buffer) => void;
  private currentInstructions: string;

  constructor() {
    const apiKey = process.env['OPENAI_API_KEY'];
    if (!apiKey) {
      throw new Error('OPENAI_API_KEY environment variable is required');
    }

    this.currentInstructions = `You are a helpful AI assistant for Vuzix M400 smart glasses.

IMPORTANT GUIDELINES:
- Keep ALL responses very brief and concise (max 2 sentences)
- Responses should be suitable for heads-up display (HUD)
- Focus on actionable information
- Use simple, clear language
- Avoid long explanations or detailed descriptions
- For visual content, describe only the most important elements

You can help with:
- Answering questions about what you see in images
- Providing quick information and assistance
- Giving brief instructions or guidance
- Identifying objects and text in images`;

    this.realtimeClient = new RealtimeClient(apiKey);
    this.openaiClient = new OpenAI({
      apiKey: apiKey
    });

    this.setupRealtimeEvents();
    logger.info('OpenAI Bridge initialized');
  }

  private setupRealtimeEvents(): void {
    this.realtimeClient.on('connected', () => {
      this.isConnectedFlag = true;
      logger.info('Realtime client connected');
    });

    this.realtimeClient.on('disconnected', () => {
      this.isConnectedFlag = false;
      logger.info('Realtime client disconnected');
    });

    this.realtimeClient.on('text_complete', (text: string) => {
      logger.info('Text response received from OpenAI', { 
        length: text.length,
        preview: text.substring(0, 100) + '...'
      });
      this.onTextResponseCallback?.(text);
    });

    this.realtimeClient.on('text_delta', (text: string) => {
      // Handle streaming text if needed
      logger.debug('Text delta received', { text });
    });

    this.realtimeClient.on('audio_complete', (audioPart: any) => {
      if (audioPart.audio) {
        const audioBuffer = Buffer.from(audioPart.audio, 'base64');
        logger.info('Audio response received from OpenAI', { size: audioBuffer.length });
        this.onAudioResponseCallback?.(audioBuffer);
      }
    });

    this.realtimeClient.on('speech_started', () => {
      logger.debug('User speech started');
    });

    this.realtimeClient.on('speech_stopped', () => {
      logger.debug('User speech stopped');
    });

    this.realtimeClient.on('api_error', (error: any) => {
      logger.error('OpenAI API error', { error });
    });

    this.realtimeClient.on('error', (error: Error) => {
      logger.error('Realtime client error', { error });
      this.isConnectedFlag = false;
    });
  }

  async initialize(): Promise<void> {
    try {
      await this.realtimeClient.connect();
      logger.info('OpenAI Bridge initialized successfully');
    } catch (error) {
      logger.error('Failed to initialize OpenAI Bridge', { error });
      throw error;
    }
  }

  async sendAudio(audioData: Buffer): Promise<void> {
    try {
      if (!this.isConnectedFlag) {
        logger.warn('Cannot send audio: not connected to OpenAI');
        return;
      }

      // Send audio data to realtime API
      // NOTE: With server_vad enabled, OpenAI automatically manages buffer commits
      // based on voice activity detection. We should NOT call commitAudio() manually.
      this.realtimeClient.sendAudio(audioData);

      logger.debug('Audio data sent to OpenAI Realtime API', { size: audioData.length });

    } catch (error) {
      logger.error('Failed to send audio to OpenAI', { error });
    }
  }

  async sendImage(imageData: Buffer): Promise<void> {
    try {
      logger.info('Processing image with OpenAI Vision', { size: imageData.length });

      // Convert image buffer to base64
      const imageBase64 = imageData.toString('base64');
      const imageUrl = `data:image/jpeg;base64,${imageBase64}`;

      // Use OpenAI client to analyze the image
      const visionPrompt = `${this.currentInstructions}\n\nAnalyze this image briefly. Describe what you see in 1-2 sentences, focusing on the most important elements. Keep it concise for a heads-up display.`;
      
      const response = await this.openaiClient.chat.completions.create({
        model: 'gpt-4o',
        messages: [{
          role: 'user',
          content: [
            { type: 'text', text: visionPrompt },
            {
              type: 'image_url',
              image_url: {
                url: imageUrl,
                detail: 'auto' // Use 'auto' for optimal balance of speed and quality
              }
            }
          ]
        }],
        max_tokens: 150,
        temperature: 0.7
      });

      const aiResponse = response.choices[0]?.message?.content;
      if (aiResponse) {
        logger.info('Vision analysis completed', { 
          response: aiResponse.substring(0, 100) + '...'
        });
        this.onTextResponseCallback?.(aiResponse);
      }

    } catch (error) {
      logger.error('Failed to process image with OpenAI', { error });
      this.onTextResponseCallback?.('Sorry, I could not analyze the image at this time.');
    }
  }

  async sendText(text: string): Promise<void> {
    try {
      if (!this.isConnectedFlag) {
        logger.warn('Cannot send text: not connected to OpenAI');
        return;
      }

      this.realtimeClient.sendText(text);
      logger.debug('Text sent to OpenAI Realtime API', { text });

    } catch (error) {
      logger.error('Failed to send text to OpenAI', { error });
    }
  }

  updateSystemPrompt(prompt: string): void {
    this.currentInstructions = prompt;

    if (this.isConnectedFlag) {
      this.realtimeClient.updateInstructions(prompt);
    }

    logger.info('System prompt updated', { 
      length: prompt.length,
      preview: prompt.substring(0, 100) + '...'
    });
  }

  updateSessionConfig(config: Partial<SessionConfig>): void {
    if (this.isConnectedFlag) {
      this.realtimeClient.updateSessionConfig(config);
      logger.info('Session configuration updated', { config });
    } else {
      logger.warn('Cannot update session config: not connected');
    }
  }

  // Helper methods for specific configurations
  enableAudioMode(): void {
    this.updateSessionConfig({
      modalities: ['text', 'audio'],
      voice: 'alloy'
    });
  }

  enableTextOnlyMode(): void {
    this.updateSessionConfig({
      modalities: ['text']
    });
  }

  setVoice(voice: 'alloy' | 'echo' | 'fable' | 'onyx' | 'nova' | 'shimmer'): void {
    this.updateSessionConfig({
      voice: voice
    });
  }

  setTemperature(temperature: number): void {
    if (temperature < 0 || temperature > 2) {
      logger.warn('Temperature should be between 0 and 2', { temperature });
      return;
    }
    
    this.updateSessionConfig({
      temperature: temperature
    });
  }

  getSystemPrompt(): string {
    return this.currentInstructions;
  }

  onTextResponse(callback: (text: string) => void): void {
    this.onTextResponseCallback = callback;
  }

  onAudioResponse(callback: (audioData: Buffer) => void): void {
    this.onAudioResponseCallback = callback;
  }

  isConnected(): boolean {
    return this.isConnectedFlag && this.realtimeClient.isReady();
  }

  async disconnect(): Promise<void> {
    logger.info('Disconnecting OpenAI Bridge');
    
    this.realtimeClient.disconnect();
    this.isConnectedFlag = false;
    
    logger.info('OpenAI Bridge disconnected');
  }

  // Health check method
  async checkHealth(): Promise<{
    realtime: boolean;
    openai: boolean;
    overall: boolean;
  }> {
    try {
      const realtimeHealthy = this.realtimeClient.isReady();
      const openaiHealthy = this.openaiClient !== null;
      
      return {
        realtime: realtimeHealthy,
        openai: openaiHealthy,
        overall: realtimeHealthy && openaiHealthy
      };
    } catch (error) {
      logger.error('Health check failed', { error });
      return {
        realtime: false,
        openai: false,
        overall: false
      };
    }
  }

  // Get current session configuration
  getSessionConfig(): SessionConfig | null {
    if (this.isConnectedFlag) {
      return this.realtimeClient.getSessionConfig();
    }
    return null;
  }

  // Utility method to create a specialized prompt for different contexts
  createContextualPrompt(context: 'navigation' | 'reading' | 'general' | 'safety'): string {
    const basePrompt = `You are a Smart Glasses AI assistant. Keep responses extremely brief (max 1-2 sentences).`;
    
    const contextPrompts = {
      navigation: `${basePrompt} Focus on directions, locations, and spatial information. Use clear, actionable language.`,
      reading: `${basePrompt} Help with reading text, documents, or signs. Provide summaries and key information.`,
      safety: `${basePrompt} Prioritize safety information. Alert about hazards, warnings, or important safety considerations.`,
      general: `${basePrompt} Provide helpful, concise assistance for everyday tasks and questions.`
    };

    return contextPrompts[context];
  }
}