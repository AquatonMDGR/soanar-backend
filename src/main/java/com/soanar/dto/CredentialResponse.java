package com.soanar.dto;

/**
 * Response for credential connection status
 */
public class CredentialResponse {

    private String platform;
    private Boolean isConnected;
    private String pageName;
    private String connectedAt;
    private Boolean isTokenValid;

    public CredentialResponse() {}

    public CredentialResponse(String platform, Boolean isConnected) {
        this.platform = platform;
        this.isConnected = isConnected;
    }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public Boolean getIsConnected() { return isConnected; }
    public void setIsConnected(Boolean isConnected) { this.isConnected = isConnected; }

    public String getPageName() { return pageName; }
    public void setPageName(String pageName) { this.pageName = pageName; }

    public String getConnectedAt() { return connectedAt; }
    public void setConnectedAt(String connectedAt) { this.connectedAt = connectedAt; }

    public Boolean getIsTokenValid() { return isTokenValid; }
    public void setIsTokenValid(Boolean isTokenValid) { this.isTokenValid = isTokenValid; }
}
