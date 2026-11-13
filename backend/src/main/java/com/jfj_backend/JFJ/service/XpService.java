package com.jfj_backend.JFJ.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.response.LevelInfo;
import com.jfj_backend.JFJ.entity.LevelConfig;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.entity.XpEvent;
import com.jfj_backend.JFJ.enums.XpEventType;
import com.jfj_backend.JFJ.exception.ResourceNotFoundException;
import com.jfj_backend.JFJ.repository.LevelConfigRepository;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.repository.XpEventRepository;

/**
 * xp_events is the source of truth; users.total_xp and users.level are a cache updated in the SAME
 * transaction as the event. Callers must lock the user row first (lock / lockAll), so two awards for
 * one user can't both read the same old total.
 */
@Service
public class XpService {

	public static final int GIG_COMPLETED_XP = 20;
	public static final int GIG_COMPLETED_PROVIDER_XP = 10;
	public static final int PROFILE_COMPLETE_XP = 10;

	private final XpEventRepository events;
	private final UserRepository users;
	private final LevelConfigRepository levels;
	private volatile List<LevelConfig> levelsByMinXpDesc;

	public XpService(XpEventRepository events, UserRepository users, LevelConfigRepository levels) {
		this.events = events;
		this.users = users;
		this.levels = levels;
	}

	public static int forRating(int rating) {
		return switch (rating) {
			case 5 -> 10;
			case 4 -> 5;
			case 3 -> 0;
			case 2 -> -5;
			case 1 -> -10;
			default -> throw new IllegalArgumentException("rating must be 1-5");
		};
	}

	/** MANDATORY: a lock taken outside a transaction would be released immediately. */
	@Transactional(propagation = Propagation.MANDATORY)
	public User lock(UUID userId) {
		return users.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	/**
	 * Locks several users in a FIXED order (sorted ids). If two transactions locked the same users in
	 * different orders, each could wait for the other forever (a deadlock); one order for everyone prevents it.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Map<UUID, User> lockAll(Collection<UUID> userIds) {
		return userIds.stream().distinct().sorted()
				.map(this::lock)
				.collect(Collectors.toMap(User::getId, user -> user));
	}

	/**
	 * Idempotent: the same (user, type, ref, version) is never awarded twice (also enforced by a UNIQUE key).
	 * Clamp: points = max(points, -total), so the total never drops below 0 and SUM(points) == total_xp.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void award(User lockedUser, XpEventType type, UUID refId, int version, int points) {
		if (events.existsByUserIdAndEventTypeAndRefIdAndVersion(lockedUser.getId(), type, refId, version)) {
			return;
		}
		int applied = Math.max(points, -lockedUser.getTotalXp());
		events.save(new XpEvent(lockedUser.getId(), type, applied, refId, version));
		lockedUser.applyXp(applied, levelFor(lockedUser.getTotalXp() + applied));
	}

	public int levelFor(int totalXp) {
		return levelTable().stream().filter(l -> totalXp >= l.getMinXp()).findFirst()
				.map(LevelConfig::getLevel).orElse(1);
	}

	public LevelInfo levelInfo(User user) {
		String name = levelTable().stream().filter(l -> l.getLevel() == user.getLevel()).findFirst()
				.map(LevelConfig::getName).orElse("Newcomer");
		return new LevelInfo(user.getLevel(), name, user.getTotalXp());
	}

	public String levelName(int level) {
		return levelTable().stream().filter(l -> l.getLevel() == level).findFirst().map(LevelConfig::getName)
				.orElse("Level " + level);
	}

	private List<LevelConfig> levelTable() {
		List<LevelConfig> table = levelsByMinXpDesc;
		if (table == null) {
			table = List.copyOf(levels.findAllByOrderByMinXpDesc());
			levelsByMinXpDesc = table;
		}
		return table;
	}
}
