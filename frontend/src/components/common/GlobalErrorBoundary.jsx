import React from "react";
import { Button } from "@carbon/react";

const getErrorText = (errorLike) => {
  if (!errorLike) {
    return "";
  }

  if (typeof errorLike === "string") {
    return errorLike;
  }

  if (errorLike instanceof Error) {
    return errorLike.message || String(errorLike);
  }

  if (typeof errorLike === "object" && errorLike.message) {
    return errorLike.message;
  }

  try {
    return JSON.stringify(errorLike);
  } catch (jsonError) {
    return String(errorLike);
  }
};

class GlobalErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      fatalError: null,
      componentStack: "",
      runtimeError: null,
    };
    this.handleWindowError = this.handleWindowError.bind(this);
    this.handleUnhandledRejection = this.handleUnhandledRejection.bind(this);
    this.dismissRuntimeError = this.dismissRuntimeError.bind(this);
    this.reloadPage = this.reloadPage.bind(this);
  }

  static getDerivedStateFromError(error) {
    return { fatalError: error };
  }

  componentDidCatch(error, errorInfo) {
    this.setState({
      fatalError: error,
      componentStack: errorInfo?.componentStack || "",
    });
    console.error("Fatal render error captured by GlobalErrorBoundary:", error);
  }

  componentDidMount() {
    window.addEventListener("error", this.handleWindowError);
    window.addEventListener(
      "unhandledrejection",
      this.handleUnhandledRejection,
    );
  }

  componentWillUnmount() {
    window.removeEventListener("error", this.handleWindowError);
    window.removeEventListener(
      "unhandledrejection",
      this.handleUnhandledRejection,
    );
  }

  handleWindowError(event) {
    if (this.state.fatalError) {
      return;
    }

    const message =
      getErrorText(event?.error) ||
      getErrorText(event?.message) ||
      this.props.runtimeFallbackMessage;
    const location =
      event?.filename && event?.lineno
        ? `${event.filename}:${event.lineno}`
        : "";

    this.setState({
      runtimeError: {
        message,
        details: location,
      },
    });
  }

  handleUnhandledRejection(event) {
    if (this.state.fatalError) {
      return;
    }

    const message =
      getErrorText(event?.reason) || this.props.runtimeFallbackMessage;

    this.setState({
      runtimeError: {
        message,
        details: "",
      },
    });
  }

  dismissRuntimeError() {
    this.setState({ runtimeError: null });
  }

  reloadPage() {
    window.location.reload();
  }

  renderFatalError() {
    const {
      fatalTitle,
      fatalDescription,
      reloadLabel,
      detailsLabel,
      runtimeFallbackMessage,
    } = this.props;
    const { fatalError, componentStack } = this.state;
    const message = getErrorText(fatalError) || runtimeFallbackMessage;
    const details = [message, componentStack].filter(Boolean).join("\n\n");

    return (
      <div className="oe-global-error-fallback" role="alert">
        <div className="oe-global-error-fallback__card">
          <h2 className="oe-global-error-fallback__title">{fatalTitle}</h2>
          <p className="oe-global-error-fallback__description">
            {fatalDescription}
          </p>
          <details>
            <summary>{detailsLabel}</summary>
            <pre className="oe-global-error-fallback__details">{details}</pre>
          </details>
          <div className="oe-global-error-fallback__actions">
            <Button kind="primary" onClick={this.reloadPage}>
              {reloadLabel}
            </Button>
          </div>
        </div>
      </div>
    );
  }

  renderRuntimeBanner() {
    const { runtimeError } = this.state;
    if (!runtimeError) {
      return null;
    }

    const { runtimeTitle, dismissLabel, reloadLabel, runtimeFallbackMessage } =
      this.props;
    const detailText = runtimeError.details ? ` (${runtimeError.details})` : "";
    const message = runtimeError.message || runtimeFallbackMessage;

    return (
      <div className="oe-global-error-banner" role="alert">
        <div className="oe-global-error-banner__message">
          <strong>{runtimeTitle}: </strong>
          <span>
            {message}
            {detailText}
          </span>
        </div>
        <div className="oe-global-error-banner__actions">
          <Button kind="ghost" size="sm" onClick={this.dismissRuntimeError}>
            {dismissLabel}
          </Button>
          <Button kind="primary" size="sm" onClick={this.reloadPage}>
            {reloadLabel}
          </Button>
        </div>
      </div>
    );
  }

  render() {
    if (this.state.fatalError) {
      return this.renderFatalError();
    }

    return (
      <>
        {this.renderRuntimeBanner()}
        {this.props.children}
      </>
    );
  }
}

GlobalErrorBoundary.defaultProps = {
  fatalTitle: "Unexpected application error",
  fatalDescription:
    "An unrecoverable error occurred while rendering this page.",
  runtimeTitle: "Runtime error",
  runtimeFallbackMessage: "An unexpected error occurred.",
  dismissLabel: "Dismiss",
  reloadLabel: "Reload",
  detailsLabel: "Technical details",
};

export default GlobalErrorBoundary;
