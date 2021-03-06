package controllers;

import static org.apache.commons.lang.StringUtils.*;
import helper.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;

import jobs.DocumentParser;
import play.Play;
import play.libs.IO;
import play.mvc.Controller;

/**
 * Documentation controller.
 * 
 * @author garbagetown
 * 
 */
public class Documentation extends Controller {

    /**
     * page action.
     * 
     * @param version
     * @param id
     */
    public static void page(String version, String id) {
        if (isEmpty(version) || version.equals("latest")) {
            redirect(String.format("/documentation/%s/%s", DocumentParser.latestVersion, id));
        }
        if (!DocumentParser.versions.contains(version)) {
            if (version.startsWith("1.0")) {
                version = "1.0.3.2";
            } else if (version.startsWith("1.1")) {
                version = "1.1.1";
            } else if (version.startsWith("1.2")) {
                version = "1.2.7";
            } else if (version.startsWith("2.0")) {
                version = "2.0.8";
            } else {
                version = DocumentParser.latestVersion;
            }
            redirect(String.format("/documentation/%s/%s", version, id));
        }
        if (isEmpty(id) || id.equalsIgnoreCase("null")) {
            String home = DocumentParser.isTextile(version) ? "home" : "Home";
            redirect(String.format("/documentation/%s/%s", version, home));
        }
        String html = null;
        try {
            if (Play.mode == Play.Mode.PROD) {
                File file = new File(Play.applicationPath, String.format("html/%s/%s.html",
                        version, id));
                if (file == null || !file.exists()) {
                    throw new FileNotFoundException();
                }
                html = IO.readContentAsString(file);
            } else {
                html = DocumentParser.parse(version, id);
            }
        } catch (FileNotFoundException e) {
            notFound(request.path);
        }
        renderHtml(html);
    }

    /**
     * 
     * @param version
     * @param name
     */
    public static void image(String version, String name) {
        String filepath = "";
        for (String s : DocumentParser.versions) {
            filepath = String.format("documentation/%s/images/%s.png", s, name);
            if (new File(Play.applicationPath, filepath).exists()) {
                break;
            }
        }
        renderBinaryFile(filepath);
    }

    /**
     * 
     * @param version
     * @param path
     */
    public static void resources(String version, String path) {
        File file = new File(Play.applicationPath, String.format("documentation/%s/%s", version,
                path));
        if (!file.exists()) {
            notFound(path);
        }
        renderBinary(file);
    }

    /**
     * 
     * @param version
     * @param name
     */
    public static void file(String version, String name) {
        renderBinaryFile(String.format("documentation/%s/files/%s", version, name));
    }

    /**
     * 
     * @param filepath
     */
    private static void renderBinaryFile(String filepath) {
        File file = new File(Play.applicationPath, filepath);
        if (!file.exists()) {
            notFound(file.getPath());
        }
        renderBinary(file);
    }

    /**
     * 
     * @param version
     * @param id
     */
    public static void cheatsheet(String version, String id) {
        final String action = "documentation";
        File dir = new File(Play.applicationPath, String.format("documentation/%s/cheatsheets/%s",
                version, id));

        if (!dir.exists()) {
            if (!version.equals(DocumentParser.latestVersion)) {
                cheatsheet(DocumentParser.latestVersion, id);
            }
            notFound(dir.getPath());
        }

        File[] files = dir.listFiles();
        Arrays.sort(files);
        String[] htmls = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            htmls[i] = DocumentParser.parseTextileFile(files[i]);
        }
        String title = StringUtils.humanize(id);

        render(action, version, id, htmls, title);
    }
}