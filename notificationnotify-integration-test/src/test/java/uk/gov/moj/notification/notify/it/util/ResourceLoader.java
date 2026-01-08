package uk.gov.moj.notification.notify.it.util;

import static com.google.common.io.Resources.getResource;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;

import com.google.common.io.Resources;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.io.IOUtils;

/**
 * Utility to help load resources for testing.
 */
public class ResourceLoader {

    private ResourceLoader() {
    }

    public static File getFileFrom(final String filePath) {
        return new File(getResource(filePath).getFile());
    }

    public static String getJsonResponse(final String jsonFile) throws IOException {
        return Resources.toString(Resources.getResource(jsonFile),
                Charset.defaultCharset());
    }

    public static byte[] getFileContentForLargeFile(final String filePath) throws IOException {
        final File pdf = getFileFrom(filePath);
        final FileChannel channel = new FileInputStream(pdf.getPath()).getChannel();
        final MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        channel.close();
        final byte[] data2 = new byte[buffer.remaining()];
        buffer.get(data2);
        return data2;
    }

    public static byte[] getFileContent(final String filePath) throws IOException {
        final File pdf = getFileFrom(filePath);
        return Files.readAllBytes(pdf.toPath());
    }

    public static String getBase64EncodedFileContent(final String filePath) throws IOException {
        final Base64InputStream base64InputStream = new Base64InputStream(new FileInputStream(getFileFrom(filePath)), true, 0, null);
        return IOUtils.toString(base64InputStream, ISO_8859_1);
    }
}
