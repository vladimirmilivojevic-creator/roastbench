package dev.roastbench.profile;

import dev.roastbench.common.ConflictException;
import dev.roastbench.common.NotFoundException;
import dev.roastbench.roast.Roast;
import dev.roastbench.roast.RoastRepository;
import dev.roastbench.roast.RoastRepository.ProfileStats;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Roast profiles and their reference roast (the "star" in the roast list). */
@Service
public class ProfileService {

    public record ProfileRequest(@NotBlank @Size(max = 120) String name, @Size(max = 2000) String description) {
    }

    public record ReferenceRoastDto(Long id, String batchCode, String name) {
    }

    public record ProfileDto(Long id, String name, String description, ReferenceRoastDto referenceRoast,
                             long roastCount, Double avgDtrPercent, Double avgLossPercent,
                             Double avgTotalTimeSeconds) {
    }

    private final ProfileRepository profiles;
    private final RoastRepository roasts;

    public ProfileService(ProfileRepository profiles, RoastRepository roasts) {
        this.profiles = profiles;
        this.roasts = roasts;
    }

    @Transactional(readOnly = true)
    public List<ProfileDto> list() {
        Map<Long, ProfileStats> stats = new HashMap<>();
        roasts.profileStats().forEach(s -> stats.put(s.getProfileId(), s));
        return profiles.findAll().stream()
                .sorted(Comparator.comparing(Profile::getName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> toDto(p, stats.get(p.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ProfileDto get(long id) {
        Profile profile = find(id);
        return toDto(profile, roasts.profileStats().stream().filter(s -> s.getProfileId().equals(id)).findFirst()
                .orElse(null));
    }

    @Transactional
    public ProfileDto create(ProfileRequest request) {
        String name = request.name().trim();
        if (profiles.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A profile named " + name + " already exists");
        }
        Profile profile = profiles.save(new Profile(name, blankToNull(request.description())));
        return toDto(profile, null);
    }

    @Transactional
    public ProfileDto update(long id, ProfileRequest request) {
        Profile profile = find(id);
        String name = request.name().trim();
        if (!profile.getName().equalsIgnoreCase(name) && profiles.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A profile named " + name + " already exists");
        }
        profile.setName(name);
        profile.setDescription(blankToNull(request.description()));
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        profiles.delete(find(id));
    }

    /** Sets (or clears, with {@code null}) the reference roast. The roast must belong to the profile. */
    @Transactional
    public ProfileDto setReference(long profileId, Long roastId) {
        Profile profile = find(profileId);
        if (roastId == null) {
            profile.setReferenceRoastId(null);
        } else {
            Roast roast = roasts.findById(roastId).orElseThrow(() -> new NotFoundException("Roast", roastId));
            if (!Long.valueOf(profileId).equals(roast.getProfileId())) {
                throw new IllegalArgumentException("Roast " + roast.batchCode() + " does not belong to profile "
                        + profile.getName() + ". Assign it to the profile first");
            }
            profile.setReferenceRoastId(roastId);
        }
        return get(profileId);
    }

    /** Moves a roast to another profile (or none); a roast that leaves a profile stops being its reference. */
    @Transactional
    public void assignRoast(long roastId, Long profileId) {
        Roast roast = roasts.findById(roastId).orElseThrow(() -> new NotFoundException("Roast", roastId));
        if (profileId != null) {
            find(profileId);
        }
        Long previous = roast.getProfileId();
        if (previous != null && !previous.equals(profileId)) {
            profiles.findById(previous).ifPresent(p -> {
                if (Long.valueOf(roastId).equals(p.getReferenceRoastId())) {
                    p.setReferenceRoastId(null);
                }
            });
        }
        roast.setProfileId(profileId);
    }

    private Profile find(long id) {
        return profiles.findById(id).orElseThrow(() -> new NotFoundException("Profile", id));
    }

    private ProfileDto toDto(Profile p, ProfileStats s) {
        ReferenceRoastDto reference = null;
        if (p.getReferenceRoastId() != null) {
            reference = roasts.findById(p.getReferenceRoastId())
                    .map(r -> new ReferenceRoastDto(r.getId(), r.batchCode(), r.getName())).orElse(null);
        }
        return new ProfileDto(p.getId(), p.getName(), p.getDescription(), reference, s == null ? 0 : s.getRoastCount(),
                s == null ? null : s.getAvgDtr(), s == null ? null : s.getAvgLoss(),
                s == null ? null : s.getAvgTotalTime());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
