package ItemBro.Advice;

import ItemBro.Advice.DTO.AdviceBody;
import ItemBro.Advice.DTO.AdviceDTO;
import ItemBro.Advice.DTO.AdvicePageDTO;
import ItemBro.Exception.*;
import ItemBro.Game.DTO.GameDTO;
import ItemBro.Game.Game;
import ItemBro.Game.GameRepository;
import ItemBro.Game.GameStatus;
import ItemBro.User.DTO.UserSellerDTO;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import ItemBro.User.UserRole;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AdviceService {

    private final static Logger logger = LoggerFactory.getLogger(AdviceService.class);

    private final AdviceRepository adviceRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    public AdviceService(AdviceRepository adviceRepository,  UserRepository userRepository,
                         GameRepository gameRepository) {
        this.adviceRepository = adviceRepository;
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
    }

    @EntityGraph(attributePaths = {"seller", "game"})
    public AdviceDTO createAdvice(AdviceBody adviceBody, Principal principal) {
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (u.isPresent()) {
            User user = u.get();
            long countOfAdvices = adviceRepository.countAdvicesBySellerIdAndStatusEquals(user.getId(),
                    AdviceStatus.ACTIVE);
            if (countOfAdvices < 5) {
                Optional<Game> g = gameRepository.findById(adviceBody.game_id());
                if (g.isPresent()) {
                    Game game = g.get();
                    GameStatus gameStatus = game.getStatus();

                    if (gameStatus != GameStatus.OPENED) throw new GameArchivedException("game in archive");

                    Advice advice = new Advice(adviceBody.name(), game, user, adviceBody.price(), AdviceStatus.ACTIVE);
                    Advice adviceFromDB =  adviceRepository.save(advice);
                    return new AdviceDTO(adviceFromDB, game, adviceFromDB.getSeller());
                } else {
                    throw new GameNotFountException("Game not found");
                }
            } else {
                throw new UserLimitAdviceException("Limit advice");
            }
        } else {
            throw new UserNotFoundException("User not found");
        }
    }

    @Transactional
    public void deleteAdvice(Long id, Principal principal) {
        Optional<Advice> a = adviceRepository.findById(id);
        if (a.isPresent()) {
            Advice advice = a.get();
            Optional<User> u = userRepository.findByUsername(principal.getName());
            if (u.isPresent()) {
                User user = u.get();
                if (Objects.equals(user.getId(), advice.getSeller().getId())) {
                    advice.setStatus(AdviceStatus.ARCHIVED);
                } else {
                    if (user.getRole() != UserRole.ADMIN) {
                        throw new NoPermissionsException("No permissions");
                    }
                    advice.setStatus(AdviceStatus.ARCHIVED);
                }
            } else {
                throw new UserNotFoundException("User not found");
            }
        } else {
            throw new AdviceNotFoundException("Advice not found");
        }
    }

    @Transactional(readOnly = true)
    public AdvicePageDTO getAdvicesByGame(Long gameId, Integer page) {
        Optional<Game> g =  gameRepository.findById(gameId);
        if (g.isEmpty()) throw new GameNotFountException("Game not found");
        Game game = g.get();
        GameStatus gameStatus = game.getStatus();

        if (gameStatus != GameStatus.OPENED) throw new GameArchivedException("game in archive");

        if (page == null) page = 0;
        if (page < 0) throw new IllegalArgumentException();
        Pageable request = PageRequest.of(page, 50);
        Page<Advice> advicePage = adviceRepository.findAdviceByGame_IdAndStatusOrderByIdDesc(gameId,
                AdviceStatus.ACTIVE, request);
        Page<AdviceDTO> adviceDTOPage = advicePage.map(advice -> {
            return new AdviceDTO(advice, game, advice.getSeller());
        });
        return new AdvicePageDTO(adviceDTOPage.getContent(), page, 50, adviceDTOPage.getTotalElements(),
                adviceDTOPage.getTotalPages());
    }

    @Transactional(rollbackFor = IOException.class)
    public AdviceDTO uploadImage(Long id, MultipartFile file, Principal principal) throws IOException {

        Optional<User> u =  userRepository.findByUsername(principal.getName());
        if (u.isEmpty()) throw new UserNotFoundException("User not found");
        User userFromRequest =  u.get();

        Optional<Advice> a = adviceRepository.findById(id);
        if (a.isEmpty()) throw new AdviceNotFoundException("Advice not found");
        Advice advice = a.get();
        AdviceStatus adviceStatus = advice.getStatus();
        if (adviceStatus == AdviceStatus.ARCHIVED) throw new AdviceNotFoundException("Advice in archive");

        Game game = advice.getGame();
        GameStatus gameStatus = game.getStatus();
        if (gameStatus != GameStatus.OPENED) throw new GameArchivedException("game not opened");

        User userSeller =  advice.getSeller();

        if (userFromRequest.getRole() != UserRole.ADMIN) {
            if (!userFromRequest.getId().equals(userSeller.getId())) {
                throw new NoPermissionsException("No permissions");
            }
        }

        if (file.isEmpty()) throw new IllegalArgumentException("Файл не может быть пустым");
        if (file.getSize() > (5L * 1024 * 1024)) throw new MaxUploadSizeExceededException(5L*1024*1024);
        String contentType = new Tika().detect(file.getBytes());
        if (!Set.of("image/jpeg", "image/png", "image/webp").contains(contentType))
            throw new IllegalArgumentException("Разрешены только JPEG, PNG, WEBP");

        Path uploadDirectory = Path.of("/app/uploads");
        Files.createDirectories(uploadDirectory);

        String fileId = UUID.randomUUID().toString();
        Path dest = uploadDirectory.resolve(fileId);

        String oldImageUrl = advice.getImageUrl();
        Path oldFile;

        if (oldImageUrl == null) {
            oldFile = null;
        } else {
            if (!oldImageUrl.startsWith("/api/media/"))
                throw new IllegalStateException("Некорректная ссылка у старого изображения");

            String oldFileId =  oldImageUrl.substring("/api/media/".length());
            UUID oldFileUUID = UUID.fromString(oldFileId);
            oldFile = uploadDirectory.resolve(oldFileId);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deleteFile(oldFile);
                } else if (status == STATUS_ROLLED_BACK) {
                    deleteFile(dest);
                } else {
                    logger.warn("Неизвестная ситуация: {}", dest);
                }
            }
        });

        file.transferTo(dest);
        String imageUrl = "/api/media/"+fileId;
        advice.setImageUrl(imageUrl);
        return new AdviceDTO(advice, game, advice.getSeller());
    }

    public void deleteFile(Path path) {
        if (path == null) return;

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("Не удалось удалить файл: {}", path, e);
        }
    }
}