package com.antfortune.freeline.idea.models;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import com.antfortune.freeline.idea.utils.Utils;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by pengwei on 16/9/11.
 */
public class GradleDependencyEntity {
    private String groupId;
    private String artifactId;
    private String version;
    private String updateTime;
    private String newestReleaseVersion;

    public String getGroupId() {
        return groupId;
    }

    public GradleDependencyEntity setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public GradleDependencyEntity setArtifactId(String artifactId) {
        this.artifactId = artifactId;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public String getUpdateTime() {
        if (Utils.notEmpty(updateTime)) {
            SimpleDateFormat df = new SimpleDateFormat("yyyyMMddhhmmss");
            try {
                Date date = df.parse(updateTime);
                return date.toString();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public GradleDependencyEntity setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
        return this;
    }

    public GradleDependencyEntity setVersion(String version) {
        this.version = version;
        setNewestReleaseVersion(version);
        return this;
    }

    /**
     * xml解析为对象
     *
     * @param text
     * @return
     */
    public static GradleDependencyEntity parse(String text) {
        GradleDependencyEntity entity = new GradleDependencyEntity();
        XmlPullParserFactory f = null;
        try {
            f = XmlPullParserFactory.newInstance();
            f.setNamespaceAware(true);
            XmlPullParser xmlPullParser = f.newPullParser();
            xmlPullParser.setInput(new InputStreamReader(new ByteArrayInputStream(text.getBytes())));
            int eventType = xmlPullParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_DOCUMENT) {

                } else if (eventType == XmlPullParser.START_TAG) {
                    String name = xmlPullParser.getName();
                    if (name.equals("groupId")) {
                        entity.setGroupId(xmlPullParser.nextText());
                    } else if (name.equals("artifactId")) {
                        entity.setArtifactId(xmlPullParser.nextText());
                    } else if (name.equals("version")) {
                        String version = xmlPullParser.nextText();
                        entity.setVersion(version);
                    } else if (name.equals("lastUpdated")) {
                        entity.setUpdateTime(xmlPullParser.nextText());
                    }
                } else if (eventType == XmlPullParser.END_TAG) {

                } else if (eventType == XmlPullParser.TEXT) {

                }
                eventType = xmlPullParser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return entity;
    }

    public String getNewestReleaseVersion() {
        return newestReleaseVersion;
    }

    private void setNewestReleaseVersion(String version) {
        if (version != null && version.split("\\.").length == 3) {
            this.newestReleaseVersion = version;
        }
    }

    @Override
    public String toString() {
        return "GradleDependencyEntity{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                ", updateTime='" + updateTime + '\'' +
                ", newestReleaseVersion='" + newestReleaseVersion + '\'' +
                '}';
    }
}
