/**
 * Site branding utilities and API functions.
 */

import {
  getFromOpenElisServer,
  postToOpenElisServer,
  putToOpenElisServerFullResponse,
  deleteFromOpenElisServerFullResponse,
} from "./Utils";
import config from "../../config.json";

const DEFAULT_BRANDING = {
  headerColor: "#ff7b00",
  primaryColor: "#ff7b00",
  secondaryColor: "#e66a00",
};

const LEGACY_DEFAULT_BRANDING = {
  headerColor: "#ff7b00",
  primaryColor: "#ff7b00",
  secondaryColor: "#393939",
};

const isLikdicomLogoPath = (url) =>
  typeof url === "string" && /likdicom_logo/i.test(url);

const normalizeColor = (value, fallback, legacyValue) => {
  if (!value || typeof value !== "string") {
    return fallback;
  }
  const normalized = value.trim().toLowerCase();
  if (!normalized || normalized === legacyValue) {
    return fallback;
  }
  return value;
};

export const normalizeBrandingResponse = (branding) => {
  if (!branding) {
    return branding;
  }

  const normalized = {
    ...branding,
    headerColor: normalizeColor(
      branding.headerColor,
      DEFAULT_BRANDING.headerColor,
      LEGACY_DEFAULT_BRANDING.headerColor,
    ),
    primaryColor: normalizeColor(
      branding.primaryColor,
      DEFAULT_BRANDING.primaryColor,
      LEGACY_DEFAULT_BRANDING.primaryColor,
    ),
    secondaryColor: normalizeColor(
      branding.secondaryColor,
      DEFAULT_BRANDING.secondaryColor,
      LEGACY_DEFAULT_BRANDING.secondaryColor,
    ),
  };

  if (isLikdicomLogoPath(normalized.headerLogoUrl)) {
    normalized.headerLogoUrl = null;
  }

  if (isLikdicomLogoPath(normalized.loginLogoUrl)) {
    normalized.loginLogoUrl = null;
  }

  if (normalized.useHeaderLogoForLogin && !normalized.headerLogoUrl) {
    normalized.useHeaderLogoForLogin = false;
  }

  return normalized;
};

const normalizeRuntimeBranding = (branding) => {
  const normalized = normalizeBrandingResponse(branding) || {};
  return {
    ...normalized,
    headerColor: DEFAULT_BRANDING.headerColor,
    primaryColor: DEFAULT_BRANDING.primaryColor,
    secondaryColor: DEFAULT_BRANDING.secondaryColor,
    // Force OpenELIS default logos at runtime to avoid legacy LIKDICOM branding
    headerLogoUrl: null,
    loginLogoUrl: null,
    useHeaderLogoForLogin: false,
  };
};

// =============================================================================
// API Functions
// =============================================================================

/**
 * Get current branding configuration
 * @param {Function} callback - Callback function to handle response
 */
export const getBranding = (callback) => {
  getFromOpenElisServer("/rest/site-branding", (response) => {
    callback(normalizeBrandingResponse(response));
  });
};

/**
 * Get branding for runtime UI rendering.
 * Runtime theme is intentionally locked to the minimalist palette.
 */
export const getRuntimeBranding = (callback) => {
  getFromOpenElisServer("/rest/site-branding", (response) => {
    callback(normalizeRuntimeBranding(response));
  });
};

/**
 * Update branding configuration
 * @param {Object} formData - Branding configuration data
 * @param {Function} callback - Callback function to handle response (receives status, errorMessage, responseData)
 * @param {Object} extraParams - Additional parameters to pass to callback
 */
export const updateBranding = (formData, callback, extraParams) => {
  const payload = JSON.stringify(formData);
  putToOpenElisServerFullResponse(
    "/rest/site-branding",
    payload,
    async (response, extraParams) => {
      const status = response.status;
      let errorMessage = null;
      let responseData = null;

      if (status === 200 || status === 201) {
        try {
          responseData = await response.json();
        } catch (e) {
          // Response might be empty, that's okay
        }
      } else {
        try {
          const errorData = await response.json();
          console.error("Backend error response:", errorData);
          // Handle validation errors (from @Valid)
          if (errorData.errors && typeof errorData.errors === "object") {
            const validationErrors = Object.entries(errorData.errors)
              .map(([field, message]) => `${field}: ${message}`)
              .join("; ");
            errorMessage = validationErrors || "Validation error";
          } else {
            // Handle other error formats
            errorMessage =
              errorData.error ||
              errorData.message ||
              errorData.globalErrors?.join("; ") ||
              "Unknown error";
          }
        } catch (e) {
          console.error("Error parsing error response:", e);
          errorMessage = `Error ${status}: ${response.statusText}`;
        }
      }

      callback(status, errorMessage, responseData, extraParams);
    },
    extraParams,
  );
};

/**
 * Remove logo file
 * @param {String} type - Logo type (header, login, favicon)
 * @param {Function} callback - Callback function to handle response
 * @param {Object} extraParams - Additional parameters to pass to callback
 */
export const removeLogo = (type, callback, extraParams) => {
  deleteFromOpenElisServerFullResponse(
    `/rest/site-branding/logo/${type}`,
    callback,
    extraParams,
  );
};

/**
 * Reset all branding to default values
 * @param {Function} callback - Callback function to handle response
 * @param {Object} extraParams - Additional parameters to pass to callback
 */
export const resetBranding = (callback, extraParams) => {
  const payload = JSON.stringify({});
  postToOpenElisServer(
    "/rest/site-branding/reset",
    payload,
    callback,
    extraParams,
  );
};

// =============================================================================
// DOM Utility Functions
// =============================================================================

/**
 * Apply branding colors to the document root element.
 * Sets CSS custom properties that Carbon components will use.
 * @param {Object} branding - Branding configuration object
 */
export const applyBrandingColors = (branding) => {
  const root = document.documentElement;
  const effectiveBranding = {
    ...DEFAULT_BRANDING,
    ...(branding || {}),
  };

  root.style.setProperty(
    "--site-branding-header",
    effectiveBranding.headerColor,
  );
  root.style.setProperty(
    "--site-branding-primary",
    effectiveBranding.primaryColor,
  );
  root.style.setProperty(
    "--site-branding-secondary",
    effectiveBranding.secondaryColor,
  );
  root.style.setProperty(
    "--cds-interactive-01",
    effectiveBranding.primaryColor,
  );
  root.style.setProperty(
    "--cds-interactive-02",
    effectiveBranding.secondaryColor,
  );
  root.style.setProperty("--cds-link-primary", effectiveBranding.primaryColor);
  root.style.setProperty(
    "--cds-link-primary-hover",
    effectiveBranding.secondaryColor,
  );
  root.style.setProperty("--cds-focus", effectiveBranding.secondaryColor);
};

/**
 * Update the document favicon.
 * @param {String} faviconUrl - URL path to the favicon
 */
export const applyFavicon = (faviconUrl) => {
  if (!faviconUrl) return;

  // Remove existing favicon links
  const existingLinks = document.querySelectorAll('link[rel*="icon"]');
  existingLinks.forEach((link) => link.remove());

  // Add new favicon link
  const link = document.createElement("link");
  link.rel = "icon";
  link.type = "image/x-icon";
  link.href = `${config.serverBaseUrl}${faviconUrl}`;
  document.head.appendChild(link);
};

/**
 * Fetch branding configuration and apply it to the DOM.
 * Applies colors and favicon.
 * @param {Function} callback - Optional callback after branding is applied
 */
export const loadAndApplyBranding = (callback) => {
  getRuntimeBranding((response) => {
    applyBrandingColors(response || DEFAULT_BRANDING);
    if (response?.faviconUrl) {
      applyFavicon(response.faviconUrl);
    }
    if (callback) {
      callback(response);
    }
  });
};
