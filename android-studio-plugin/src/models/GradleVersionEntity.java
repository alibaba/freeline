package models;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by pengwei on 16/9/11.
 */
public class GradleVersionEntity {
    private String groupId;
    private String artifactId;
    private String version;

    public String getGroupId() {
        return groupId;
    }

    public GradleVersionEntity setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public GradleVersionEntity setArtifactId(String artifactId) {
        this.artifactId = artifactId;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public GradleVersionEntity setVersion(String version) {
        this.version = version;
        return this;
    }

    /**
     * xml解析为对象
     *
     * @param text
     * @return
     */
    public static GradleVersionEntity parse(String text) {
        GradleVersionEntity entity = new GradleVersionEntity();
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
                    }
                    if (name.equals("artifactId")) {
                        entity.setArtifactId(xmlPullParser.nextText());
                    }
                    if (name.equals("version")) {
                        entity.setVersion(xmlPullParser.nextText());
                    }
                } else if (eventType == XmlPullParser.END_TAG) {

                } else if (eventType == XmlPullParser.TEXT) {

                }
                eventType = xmlPullParser.next();
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return entity;
    }

    @Override
    public String toString() {
        return "GradleVersionEntity{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
