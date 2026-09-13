package vip.mate.dataagent.service;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.DatasetReadException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class S3DatasetFileStorageServiceTest {
    @Container static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2024-12-18T13-15-44Z").withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER","minioadmin").withEnv("MINIO_ROOT_PASSWORD","minioadmin").withCommand("server /data")
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Test void storesAndScopesPersistentFile() throws Exception {
        var service = new vip.mate.dataagent.service.impl.S3DatasetFileStorageService(endpoint(),"minioadmin","minioadmin","mateclaw-files");
        var context = new DatasetAccessContext(7L, 2L, "upload-2", Set.of());
        var ref = service.put(context,2L,"orders.csv",new ByteArrayInputStream("id\n1\n".getBytes(StandardCharsets.UTF_8)),5);
        try(var in=service.open(context,ref)){assertEquals("id\n1\n",new String(in.readAllBytes(),StandardCharsets.UTF_8));}
        assertThrows(DatasetReadException.class,()->service.open(new DatasetAccessContext(8L,2L,"x",Set.of()),ref));
        service.delete(context,ref); service.delete(context,ref);
    }
    @Test void rejectsUnsupportedExtension(){ var service=new vip.mate.dataagent.service.impl.S3DatasetFileStorageService(endpoint(),"minioadmin","minioadmin","mateclaw-files"); assertThrows(DatasetReadException.class,()->service.put(new DatasetAccessContext(1L,1L,"u",Set.of()),1L,"x.exe",new ByteArrayInputStream(new byte[0]),0)); }
    @Test void rejectsXlsxZipBombByCompressionRatio() throws Exception {
        var service = new vip.mate.dataagent.service.impl.S3DatasetFileStorageService(endpoint(),"minioadmin","minioadmin","mateclaw-files");
        byte[] payload = new byte[2 * 1024 * 1024];
        java.util.Arrays.fill(payload, (byte) 'a');
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zip.write(payload);
            zip.closeEntry();
        }
        byte[] archive = bytes.toByteArray();

        assertThrows(DatasetReadException.class, () -> service.put(
                new DatasetAccessContext(1L, 1L, "u", Set.of()), 1L, "bomb.xlsx",
                new ByteArrayInputStream(archive), archive.length));
    }
    private String endpoint(){return "http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000);}
}
