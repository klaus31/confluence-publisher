package org.sahli.asciidoc.confluence.publisher.converter;

public class AsciidocOptions {

    private String imagesOutDir;

    private final String templateDir;

    public AsciidocOptions(String templateDir) {
        this.templateDir = templateDir;
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
}
