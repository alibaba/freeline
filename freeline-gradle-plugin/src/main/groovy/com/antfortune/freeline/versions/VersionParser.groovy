package com.antfortune.freeline.versions

/**
 * ref @{org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser}
 * in gradle-2.4
 */
public class VersionParser {

    public Version transform(String original) {
        List<String> parts = new ArrayList<String>();
        boolean digit = false;
        int startPart = 0;
        int pos = 0;
        int endBase = 0;
        int endBaseStr = 0;
        for (; pos < original.length(); pos++) {
            char ch = original.charAt(pos);
            if (ch == '.' || ch == '_' || ch == '-' || ch == '+') {
                parts.add(original.substring(startPart, pos));
                startPart = pos + 1;
                digit = false;
                if (ch != '.' && endBaseStr == 0) {
                    endBase = parts.size();
                    endBaseStr = pos;
                }
            } else if (ch >= '0' && ch <= '9') {
                if (!digit && pos > startPart) {
                    if (endBaseStr == 0) {
                        endBase = parts.size() + 1;
                        endBaseStr = pos;
                    }
                    parts.add(original.substring(startPart, pos));
                    startPart = pos;
                }
                digit = true;
            } else {
                if (digit) {
                    if (endBaseStr == 0) {
                        endBase = parts.size() + 1;
                        endBaseStr = pos;
                    }
                    parts.add(original.substring(startPart, pos));
                    startPart = pos;
                }
                digit = false;
            }
        }
        if (pos > startPart) {
            parts.add(original.substring(startPart, pos));
        }
        Version base = null;
        if (endBaseStr > 0) {
            base = new Version(original.substring(0, endBaseStr), parts.subList(0, endBase), null);
        }
        return new Version(original, parts, base);
    }


}
