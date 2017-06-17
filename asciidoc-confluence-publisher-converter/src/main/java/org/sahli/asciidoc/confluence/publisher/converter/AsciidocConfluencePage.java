/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sahli.asciidoc.confluence.publisher.converter;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.ast.Title;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.stream;
import static java.util.Collections.unmodifiableMap;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang.StringEscapeUtils.unescapeHtml;
import static org.asciidoctor.Asciidoctor.Factory.create;
import static org.asciidoctor.SafeMode.UNSAFE;
import static org.asciidoctor.internal.IOUtils.readFull;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class AsciidocConfluencePage {

    private static final Pattern CDATA_PATTERN = compile("<!\\[CDATA\\[.*?\\]\\]>", DOTALL);
    private static final Pattern ATTACHMENT_PATH_PATTERN = compile("<ri:attachment ri:filename=\"(.*?)\"");
    private static final Pattern PAGE_TITLE_PATTERN = compile("<ri:page ri:content-title=\"(.*?)\"");

    private static final Asciidoctor ASCIIDOCTOR = create();

    static {
        ASCIIDOCTOR.requireLibrary("asciidoctor-diagram");
    }

    private final String pageTitle;
    private final String htmlContent;
    private final Map<String, String> attachments;

    private AsciidocConfluencePage(String pageTitle, String htmlContent, Map<String, String> attachments) {
        this.pageTitle = pageTitle;
        this.htmlContent = htmlContent;
        this.attachments = attachments;
    }

    public String content() {
        return this.htmlContent;
    }

    public String pageTitle() {
        return this.pageTitle;
    }

    public Map<String, String> attachments() {
        return unmodifiableMap(this.attachments);
    }

    public static AsciidocConfluencePage newAsciidocConfluencePage(InputStream adoc, Path pagePath, AsciidocOptions asciidocOptions) {
        Map<String, String> attachmentCollector = new HashMap<>();

        String adocContent = readFull(adoc);

        Options options = options(parentFolder(pagePath), asciidocOptions);
        String pageContent = convertedContent(adocContent, options, pagePath, attachmentCollector);

        String pageTitle = pageTitle(adocContent);

        return new AsciidocConfluencePage(pageTitle, pageContent, attachmentCollector);
    }

    private static String deriveAttachmentName(String path) {
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }

    private static String convertedContent(String adocContent, Options options, Path pagePath, Map<String, String> attachmentCollector) {
        String content = ASCIIDOCTOR.convert(adocContent, options);
        String postProcessedContent = postProcessContent(content,
                replaceCrossReferenceTargets(pagePath),
                collectAndReplaceAttachmentFileNames(attachmentCollector),
                unescapeCdataHtmlContent()
        );

        return postProcessedContent;
    }

    private static Function<String, String> unescapeCdataHtmlContent() {
        return (content) -> replaceAll(content, CDATA_PATTERN, (matchResult) -> unescapeHtml(matchResult.group()));
    }

    private static Function<String, String> collectAndReplaceAttachmentFileNames(Map<String, String> attachmentCollector) {
        return (content) -> replaceAll(content, ATTACHMENT_PATH_PATTERN, (matchResult) -> {
            String attachmentPath = matchResult.group(1);
            String attachmentFileName = deriveAttachmentName(attachmentPath);

            attachmentCollector.put(attachmentPath, attachmentFileName);

            return "<ri:attachment ri:filename=\"" + attachmentFileName + "\"";
        });
    }

    private static String replaceAll(String content, Pattern pattern, Function<MatchResult, String> replacer) {
        StringBuffer replacedContent = new StringBuffer();
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            matcher.appendReplacement(replacedContent, replacer.apply(matcher.toMatchResult()));
        }

        matcher.appendTail(replacedContent);

        return replacedContent.toString();
    }

    @SafeVarargs
    private static String postProcessContent(String initialContent, Function<String, String>... postProcessors) {
        return stream(postProcessors).reduce(initialContent, (accumulator, postProcessor) -> postProcessor.apply(accumulator), unusedCombiner());
    }

    private static String pageTitle(String pageContent) {
        return Optional.ofNullable(ASCIIDOCTOR.readDocumentHeader(pageContent).getDocumentTitle())
                .map(Title::getMain)
                .orElseThrow(() -> new RuntimeException("top-level heading or title meta information must be set"));
    }

    private static File parentFolder(Path pagePath) {
        return pagePath.getParent().toFile();
    }

    private static Options options(File baseDir, AsciidocOptions asciidocOptions) {
        File templateDirFolder = new File(asciidocOptions.getTemplateDir());

        if (!templateDirFolder.exists()) {
            throw new RuntimeException("templateDir folder does not exist");
        }

        if (!templateDirFolder.isDirectory()) {
            throw new RuntimeException("templateDir folder is not a folder");
        }

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("imagesoutdir", asciidocOptions.getImagesOutDir());
        attributes.put("outdir", asciidocOptions.getImagesOutDir());

        return OptionsBuilder.options()
                .backend("html")
                .safe(UNSAFE)
                .baseDir(baseDir)
                .templateDirs(templateDirFolder)
                .attributes(attributes)
                .get();
    }

    private static Function<String, String> replaceCrossReferenceTargets(Path pagePath) {
        return (content) -> replaceAll(content, PAGE_TITLE_PATTERN, (matchResult) -> {
            String htmlTarget = matchResult.group(1);
            Path referencedPagePath = pagePath.getParent().resolve(Paths.get(htmlTarget.substring(0, htmlTarget.lastIndexOf('.')) + ".adoc"));

            try {
                String referencedPageContent = readFull(new FileInputStream(referencedPagePath.toFile()));
                String referencedPageTitle = pageTitle(referencedPageContent);

                return "<ri:page ri:content-title=\"" + referencedPageTitle + "\"";
            } catch (FileNotFoundException e) {
                throw new RuntimeException("unable to find cross-referenced page '" + referencedPagePath + "'", e);
            }
        });
    }

    private static BinaryOperator<String> unusedCombiner() {
        return (a, b) -> a;
    }

}
