package ItemBro.Media;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class MediaController {

    private final MediaService mediaService;
    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/api/media/{fileId}")
    public ResponseEntity<Resource> getMedia(@PathVariable("fileId") String fileId) throws IOException {
        MediaFile mediaFile = mediaService.getMediaFile(fileId);

        Resource resource = new FileSystemResource(mediaFile.path());

        return ResponseEntity.ok().contentType(
                MediaType.parseMediaType(mediaFile.contentType())
        ).body(resource);
    }
}
