package org.sahli.asciidoc.confluence.publisher.converter;

import java.util.Map;

public class AsciidocOptions {

    private final Map<String, Object> attributes;
    private String imagesOutDir;

    private final String templateDir;

    public AsciidocOptions(String templateDir, Map<String, Object> attributes) {
        this.templateDir = templateDir;
        this.attributes=attributes;
    }

    public void setImagesOutDir(String imagesOutDir) {
        this.imagesOutDir = imagesOutDir;
    }

    String getImagesOutDir() {
        return imagesOutDir;
    }

    String getTemplateDir() {
        return templateDir;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
