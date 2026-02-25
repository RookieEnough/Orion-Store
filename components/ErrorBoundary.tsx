import React, { Component, ErrorInfo, ReactNode } from 'react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

/**
 * Global Error Boundary component
 * Catches React errors and displays a fallback UI
 */
class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null
    };
  }

  static getDerivedStateFromError(error: Error): State {
    return {
      hasError: true,
      error,
      errorInfo: null
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    this.setState({
      error,
      errorInfo
    });
    
    // Call custom error handler
    if (this.props.onError) {
      this.props.onError(error, errorInfo);
    }
    
    // Log to console in development
    if (process.env.NODE_ENV === 'development') {
      console.error('ErrorBoundary caught an error:', error, errorInfo);
    }
  }

  handleReset = (): void => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null
    });
  };

  render(): ReactNode {
    if (this.state.hasError) {
      // Custom fallback UI
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="min-h-screen bg-surface dark:bg-surface-dark flex items-center justify-center p-4">
          <div className="max-w-md w-full bg-card dark:bg-card-dark rounded-3xl p-8 shadow-xl border border-theme-border">
            <div className="text-center">
              {/* Error Icon */}
              <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-red-100 dark:bg-red-900/30 flex items-center justify-center">
                <i className="fas fa-exclamation-triangle text-3xl text-red-500"></i>
              </div>
              
              {/* Error Title */}
              <h1 className="text-2xl font-bold text-theme-text dark:text-white mb-2">
                Oops! Something went wrong
              </h1>
              
              {/* Error Message */}
              <p className="text-theme-sub dark:text-gray-400 mb-6">
                {this.state.error?.message || 'An unexpected error occurred'}
              </p>
              
              {/* Action Buttons */}
              <div className="space-y-3">
                <button
                  onClick={this.handleReset}
                  className="w-full py-3 px-6 bg-primary hover:bg-primary-dark text-white font-bold rounded-xl transition-colors"
                >
                  Try Again
                </button>
                
                <button
                  onClick={() => window.location.reload()}
                  className="w-full py-3 px-6 bg-theme-element hover:bg-theme-hover text-theme-text dark:text-white font-bold rounded-xl transition-colors"
                >
                  Reload App
                </button>
              </div>
              
              {/* Error Details (Development only) */}
              {process.env.NODE_ENV === 'development' && this.state.errorInfo && (
                <details className="mt-6 text-left">
                  <summary className="cursor-pointer text-sm text-theme-sub hover:text-primary">
                    Show error details
                  </summary>
                  <pre className="mt-2 p-4 bg-gray-100 dark:bg-gray-900 rounded-lg text-xs overflow-auto max-h-40">
                    {this.state.error?.stack}
                  </pre>
                </details>
              )}
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
