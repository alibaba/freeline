package com.antfortune.freeline.versions

/**
 * ref @{org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser#DefaultVersion}
 * in gradle-2.4
 */
public class Version {
    private final String source;
    private final String[] parts;
    private final Version baseVersion;

    public Version(String source, List<String> parts, Version baseVersion) {
        this.source = source;
        this.parts = parts.toArray(new String[parts.size()]);
        this.baseVersion = baseVersion == null ? this : baseVersion;
    }

    @Override
    public String toString() {
        return source;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        Version other = (Version) obj;
        return source.equals(other.source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }

    public String[] getParts() {
        return parts;
    }
}
