package uz.tubeforge.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.domain.Language;
import uz.tubeforge.repository.AppUserRepository;
import uz.tubeforge.telegram.model.TgUser;

import java.time.Clock;
import java.time.Instant;

@Service
public class UserService {
    private final AppUserRepository repository;
    private final Clock clock;

    public UserService(AppUserRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public AppUser getOrCreate(TgUser telegramUser) {
        Instant now = clock.instant();
        AppUser user = repository.findById(telegramUser.id()).orElseGet(() -> new AppUser(
                telegramUser.id(), telegramUser.username(), telegramUser.firstName(), telegramUser.lastName(),
                Language.fromTelegram(telegramUser.languageCode()), now));
        user.touch(telegramUser.username(), telegramUser.firstName(), telegramUser.lastName(), now);
        return repository.save(user);
    }

    @Transactional(readOnly = true)
    public AppUser require(long userId) {
        return repository.findById(userId).orElseThrow(() -> new IllegalStateException("Unknown user"));
    }

    @Transactional
    public AppUser acceptTerms(long userId) {
        AppUser user = require(userId);
        user.acceptTerms(clock.instant());
        return repository.save(user);
    }

    @Transactional
    public AppUser changeLanguage(long userId, Language language) {
        AppUser user = require(userId);
        user.setLanguage(language);
        return repository.save(user);
    }

    @Transactional
    public AppUser changeVideoQuality(long userId, String quality) {
        AppUser user = require(userId);
        user.setDefaultVideoQuality(quality);
        return repository.save(user);
    }

    @Transactional
    public AppUser changeAudioFormat(long userId, String format) {
        AppUser user = require(userId);
        user.setDefaultAudioFormat(format);
        return repository.save(user);
    }

    @Transactional
    public AppUser toggleDocument(long userId) {
        AppUser user = require(userId);
        user.setSendAsDocument(!user.isSendAsDocument());
        return repository.save(user);
    }

    @Transactional
    public AppUser toggleCompression(long userId) {
        AppUser user = require(userId);
        user.setAutoCompress(!user.isAutoCompress());
        return repository.save(user);
    }
}
