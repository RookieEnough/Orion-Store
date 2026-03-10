

declare module '*.css';
declare module '*.png';
declare module '*.jpg';
declare module '*.jpeg';
declare module '*.svg';
declare module '*.gif';

// Vite Worker Import Support
declare module '*?worker' {
    const workerConstructor: {
        new(): Worker;
    };
    export default workerConstructor;
}

// Manually declare the module to prevent build failures if types are missing in the environment
declare module '@capacitor/local-notifications' {
    export interface LocalNotificationSchema {
        title: string;
        body: string;
        id: number;
        schedule?: { at: Date; allowWhileIdle?: boolean };
        channelId?: string;
        smallIcon?: string;
        extra?: any;
    }

    export interface ActionPerformed {
        actionId: string;
        notification: LocalNotificationSchema;
    }

    export interface ScheduleOptions {
        notifications: LocalNotificationSchema[];
    }

    export interface Channel {
        id: string;
        name: string;
        description?: string;
        importance?: 1 | 2 | 3 | 4 | 5;
        visibility?: -1 | 0 | 1;
    }

    export interface PermissionStatus {
        display: 'granted' | 'denied' | 'prompt';
    }

    export const LocalNotifications: {
        schedule(options: ScheduleOptions): Promise<any>;
        requestPermissions(): Promise<PermissionStatus>;
        createChannel(channel: Channel): Promise<void>;
        addListener(eventName: 'localNotificationActionPerformed', listenerFunc: (action: ActionPerformed) => void): Promise<{ remove: () => Promise<void> }>;
    };
}

declare module '@capacitor/haptics' {
    export enum ImpactStyle {
        Heavy = 'HEAVY',
        Medium = 'MEDIUM',
        Light = 'LIGHT'
    }
    export enum NotificationType {
        Success = 'SUCCESS',
        Warning = 'WARNING',
        Error = 'ERROR'
    }
    export interface ImpactOptions {
        style: ImpactStyle;
    }
    export interface NotificationOptions {
        type: NotificationType;
    }
    export const Haptics: {
        impact(options: ImpactOptions): Promise<void>;
        notification(options: NotificationOptions): Promise<void>;
        selection(): Promise<void>;
        vibrate(): Promise<void>;
    };
}

declare module '@capacitor/app' {
    export interface AppState {
        isActive: boolean;
    }
    export interface AppRestoredResult {
        pluginId: string;
        methodName: string;
        data?: any;
        error?: any;
    }
    /* Added interface for backButton event data */
    export interface BackButtonEvent {
        canGoBack: boolean;
    }
    export const App: {
        addListener(eventName: 'appStateChange', listenerFunc: (state: AppState) => void): Promise<{ remove: () => Promise<void> }>;
        addListener(eventName: 'appRestoredResult', listenerFunc: (result: AppRestoredResult) => void): Promise<{ remove: () => Promise<void> }>;
        addListener(eventName: 'resume', listenerFunc: () => void): Promise<{ remove: () => Promise<void> }>;
        /* Added missing backButton overload to satisfy TypeScript requirements in App.tsx */
        addListener(eventName: 'backButton', listenerFunc: (data: BackButtonEvent) => void): Promise<{ remove: () => Promise<void> }>;
        exitApp(): Promise<void>;
    };
}

declare module 'capacitor-unity-ads' {
    export const UnityAds: {
        initialize(options: { gameId: string, testMode?: boolean }): Promise<void>;
        showRewardedVideo(options: { placementId: string }): Promise<void>;
        loadRewardedVideo(options: { placementId: string }): Promise<void>;
    };
}
