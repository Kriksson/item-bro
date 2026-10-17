package ItemBro.Media;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaService {

    public MediaFile getMediaFile(String fileId) throws IOException {
        Path uploadDirectory = Path.of("/app/uploads");
        Files.createDirectories(uploadDirectory);

        UUID id = UUID.fromString(fileId);
        Path filePath = uploadDirectory.resolve(id.toString());

        if (!Files.isRegularFile(filePath)) throw new FileNotFoundException("File not found");

        String contentType = new Tika().detect(filePath);


        if (!Set.of("image/jpeg", "image/webp", "image/png").contains(contentType))
            throw new IllegalArgumentException("invalid media");

        return new MediaFile(filePath, contentType);
    }
}
