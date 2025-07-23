import winston from 'winston';
import path from 'path';
import fs from 'fs';

export class Logger {
  private static instance: winston.Logger;

  static getInstance(): winston.Logger {
    if (!Logger.instance) {
      Logger.instance = Logger.createLogger();
    }
    return Logger.instance;
  }

  private static createLogger(): winston.Logger {
    // Ensure logs directory exists
    const logsDir = path.join(process.cwd(), 'logs');
    if (!fs.existsSync(logsDir)) {
      fs.mkdirSync(logsDir, { recursive: true });
    }

    const logLevel = process.env['LOG_LEVEL'] ?? 'info';
    const logFile = process.env['LOG_FILE'] ?? './logs/companion.log';

    return winston.createLogger({
      level: logLevel,
      format: winston.format.combine(
        winston.format.timestamp(),
        winston.format.errors({ stack: true }),
        winston.format.json()
      ),
      defaultMeta: { service: 'smart-companion-desktop' },
      transports: [
        // Write all logs to file
        new winston.transports.File({ 
          filename: logFile,
          maxsize: 5242880, // 5MB
          maxFiles: 5
        }),
        
        // Write errors to separate file
        new winston.transports.File({ 
          filename: './logs/error.log', 
          level: 'error',
          maxsize: 5242880,
          maxFiles: 3
        })
      ]
    });
  }

  static addConsoleTransport(): void {
    const logger = Logger.getInstance();
    
    logger.add(new winston.transports.Console({
      format: winston.format.combine(
        winston.format.colorize(),
        winston.format.simple(),
        winston.format.printf(({ level, message, timestamp, ...meta }) => {
          const metaStr = Object.keys(meta).length ? ` ${JSON.stringify(meta)}` : '';
          return `${timestamp as string} [${level}]: ${message as string}${metaStr}`;
        })
      )
    }));
  }
}

// Add console transport in development
if (process.env['NODE_ENV'] !== 'production') {
  Logger.addConsoleTransport();
}