package fundcopilot.observation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fund-copilot.observation")
public class ObservationProperties {
    private int universeSize = 100;
    private int universeCandidateSize = 300;
    private int rankingSize = 10;
    private int entryStreakDays = 3;
    private int exitStreakDays = 3;
    private int syncHistorySize = 320;
    private long syncIntervalMs = 300;
    private int jobTimeoutMinutes = 120;
    private boolean adminEndpointsEnabled = true;

    public int getUniverseSize() { return universeSize; }
    public void setUniverseSize(int universeSize) { this.universeSize = universeSize; }
    public int getUniverseCandidateSize() { return universeCandidateSize; }
    public void setUniverseCandidateSize(int universeCandidateSize) { this.universeCandidateSize = universeCandidateSize; }
    public int getRankingSize() { return rankingSize; }
    public void setRankingSize(int rankingSize) { this.rankingSize = rankingSize; }
    public int getEntryStreakDays() { return entryStreakDays; }
    public void setEntryStreakDays(int entryStreakDays) { this.entryStreakDays = entryStreakDays; }
    public int getExitStreakDays() { return exitStreakDays; }
    public void setExitStreakDays(int exitStreakDays) { this.exitStreakDays = exitStreakDays; }
    public int getSyncHistorySize() { return syncHistorySize; }
    public void setSyncHistorySize(int syncHistorySize) { this.syncHistorySize = syncHistorySize; }
    public long getSyncIntervalMs() { return syncIntervalMs; }
    public void setSyncIntervalMs(long syncIntervalMs) { this.syncIntervalMs = syncIntervalMs; }
    public int getJobTimeoutMinutes() { return jobTimeoutMinutes; }
    public void setJobTimeoutMinutes(int jobTimeoutMinutes) { this.jobTimeoutMinutes = jobTimeoutMinutes; }
    public boolean isAdminEndpointsEnabled() { return adminEndpointsEnabled; }
    public void setAdminEndpointsEnabled(boolean adminEndpointsEnabled) {
        this.adminEndpointsEnabled = adminEndpointsEnabled;
    }
}
