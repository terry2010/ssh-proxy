declare module "zmodem.js-ex" {
  export interface ZmodemSentry {
    consume(input: ArrayBuffer | Uint8Array | number[]): void;
    get_confirmed_session(): ZmodemSession | null;
  }

  export interface ZmodemSentryOptions {
    to_terminal: (octets: number[]) => void;
    sender: (octets: number[]) => void;
    on_detect: (detection: ZmodemDetection) => void;
    on_retract: () => void;
  }

  export interface ZmodemDetection {
    type: "send" | "receive";
    confirm(): ZmodemSession;
    deny(): void;
  }

  export interface ZmodemSession {
    type: "send" | "receive";
    on(event: "offer", handler: (xfer: ZmodemTransfer) => void): void;
    on(event: "session_end", handler: () => void): void;
    on(event: "file_end", handler: () => void): void;
    start(): Promise<ZmodemTransfer | void>;
    send_offer(details: ZmodemFileDetails): Promise<ZmodemTransfer>;
    close(): Promise<void>;
    abort(): void;
    aborted(): boolean;
    has_ended(): boolean;
    get_trailing_bytes(): number[];
  }

  export interface ZmodemTransfer {
    get_offset(): number;
    get_details(): ZmodemFileDetails;
    get_payloads(): Uint8Array[];
    accept(): Promise<Uint8Array[]>;
    skip(): void;
    send(piece: Uint8Array): void;
    end(piece: Uint8Array): Promise<void>;
    on(event: "input", handler: (data: Uint8Array) => void): void;
    on(event: "complete", handler: () => void): void;
  }

  export interface ZmodemFileDetails {
    name: string;
    size: number;
    mtime: Date;
    mode?: number;
    serial?: number;
    files_remaining: number;
    bytes_remaining: number;
  }

  export const Sentry: new (options: ZmodemSentryOptions) => ZmodemSentry;

  // Allow namespace import for monkey-patching
  const Zmodem: {
    Sentry: typeof Sentry;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    Session: any;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    [key: string]: any;
  };
  export default Zmodem;
}
