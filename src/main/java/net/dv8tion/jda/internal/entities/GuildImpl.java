/*
 * Copyright 2015 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.internal.entities;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.TLongSet;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.Region;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.automod.AutoModRule;
import net.dv8tion.jda.api.entities.automod.build.AutoModRuleData;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.entities.guild.SecurityIncidentActions;
import net.dv8tion.jda.api.entities.guild.SecurityIncidentDetections;
import net.dv8tion.jda.api.entities.sticker.GuildSticker;
import net.dv8tion.jda.api.entities.sticker.StandardSticker;
import net.dv8tion.jda.api.entities.sticker.StickerSnowflake;
import net.dv8tion.jda.api.entities.templates.Template;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.exceptions.ParsingException;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.PrivilegeConfig;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.privileges.IntegrationPrivilege;
import net.dv8tion.jda.api.managers.*;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.requests.restaction.*;
import net.dv8tion.jda.api.requests.restaction.order.CategoryOrderAction;
import net.dv8tion.jda.api.requests.restaction.order.ChannelOrderAction;
import net.dv8tion.jda.api.requests.restaction.order.RoleOrderAction;
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.cache.*;
import net.dv8tion.jda.api.utils.concurrent.Task;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.entities.automod.AutoModRuleImpl;
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.GuildChannelMixin;
import net.dv8tion.jda.internal.handle.EventCache;
import net.dv8tion.jda.internal.interactions.CommandDataImpl;
import net.dv8tion.jda.internal.interactions.command.CommandImpl;
import net.dv8tion.jda.internal.managers.*;
import net.dv8tion.jda.internal.requests.*;
import net.dv8tion.jda.internal.requests.restaction.*;
import net.dv8tion.jda.internal.requests.restaction.order.CategoryOrderActionImpl;
import net.dv8tion.jda.internal.requests.restaction.order.ChannelOrderActionImpl;
import net.dv8tion.jda.internal.requests.restaction.order.RoleOrderActionImpl;
import net.dv8tion.jda.internal.requests.restaction.pagination.AuditLogPaginationActionImpl;
import net.dv8tion.jda.internal.requests.restaction.pagination.BanPaginationActionImpl;
import net.dv8tion.jda.internal.utils.Checks;
import net.dv8tion.jda.internal.utils.EntityString;
import net.dv8tion.jda.internal.utils.Helpers;
import net.dv8tion.jda.internal.utils.UnlockHook;
import net.dv8tion.jda.internal.utils.cache.*;
import net.dv8tion.jda.internal.utils.concurrent.task.GatewayTask;
import okhttp3.MediaType;
import okhttp3.MultipartBody;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GuildImpl implements Guild
{
    private final long id;
    private final JDAImpl api;

    private final SortedSnowflakeCacheViewImpl<ScheduledEvent> scheduledEventCache = new SortedSnowflakeCacheViewImpl<>(ScheduledEvent.class, ScheduledEvent::getName, Comparator.naturalOrder());
    private final SortedChannelCacheViewImpl<GuildChannel> channelCache = new SortedChannelCacheViewImpl<>(GuildChannel.class);
    private final SortedSnowflakeCacheViewImpl<Role> roleCache = new SortedSnowflakeCacheViewImpl<>(Role.class, Role::getName, Comparator.reverseOrder());
    private final SnowflakeCacheViewImpl<RichCustomEmoji> emojicache = new SnowflakeCacheViewImpl<>(RichCustomEmoji.class, RichCustomEmoji::getName);
    private final SnowflakeCacheViewImpl<GuildSticker> stickerCache = new SnowflakeCacheViewImpl<>(GuildSticker.class, GuildSticker::getName);
    private final MemberCacheViewImpl memberCache = new MemberCacheViewImpl();
    private final CacheView.SimpleCacheView<MemberPresenceImpl> memberPresences;
    private final SnowflakeCacheViewImpl<GuildVoiceStateImpl> voiceStateCache = new SnowflakeCacheViewImpl<>(GuildVoiceStateImpl.class, state -> state.getMember().getEffectiveName());

    private CompletableFuture<Void> pendingRequestToSpeak;

    private Member owner;
    private String name;
    private String iconId, splashId;
    private String vanityCode;
    private String description, banner;
    private int maxPresences, maxMembers;
    private int boostCount;
    private long ownerId;
    private Set<String> features;
    private VoiceChannel afkChannel;
    private TextChannel systemChannel;
    private TextChannel rulesChannel;
    private TextChannel communityUpdatesChannel;
    private TextChannel safetyAlertsChannel;
    private Role publicRole;
    private SecurityIncidentActions securityIncidentActions = SecurityIncidentActions.disabled();
    private SecurityIncidentDetections securityIncidentDetections = SecurityIncidentDetections.EMPTY;
    private VerificationLevel verificationLevel = VerificationLevel.UNKNOWN;
    private NotificationLevel defaultNotificationLevel = NotificationLevel.UNKNOWN;
    private MFALevel mfaLevel = MFALevel.UNKNOWN;
    private ExplicitContentLevel explicitContentLevel = ExplicitContentLevel.UNKNOWN;
    private NSFWLevel nsfwLevel = NSFWLevel.UNKNOWN;
    private Timeout afkTimeout;
    private BoostTier boostTier = BoostTier.NONE;
    private DiscordLocale preferredLocale = DiscordLocale.ENGLISH_US;
    private int memberCount;
    private boolean boostProgressBarEnabled;

    public GuildImpl(JDAImpl api, long id)
    {
        this.id = id;
        this.api = api;
        if (api.getCacheFlags().stream().anyMatch(CacheFlag::isPresence))
            memberPresences = new CacheView.SimpleCacheView<>(MemberPresenceImpl.class, null);
        else
            memberPresences = null;
    }

    @Override
    public boolean isDetached()
    {
        return false;
    }

    public void invalidate()
    {
        //Remove everything from global cache
        // this prevents some race-conditions for getting audio managers from guilds
        getJDA().getGuildsView().remove(id);

        ChannelCacheViewImpl<Channel> channelsView = getJDA().getChannelsView();
        try (UnlockHook hook = channelsView.writeLock())
        {
            getChannels().forEach(channel -> channelsView.remove(channel.getType(), channel.getIdLong()));
        }

        // Clear audio connection
        getJDA().getClient().removeAudioConnection(id);
        final AbstractCacheView<AudioManager> audioManagerView = getJDA().getAudioManagersView();
        final AudioManagerImpl manager = (AudioManagerImpl) audioManagerView.get(id); //read-lock access/release
        if (manager != null)
            manager.closeAudioConnection(ConnectionStatus.DISCONNECTED_REMOVED_FROM_GUILD); //connection-lock access/release
        audioManagerView.remove(id); //write-lock access/release

        //cleaning up all users that we do not share a guild with anymore
        // Anything left in memberIds will be removed from the main userMap
        //Use a new HashSet so that we don't actually modify the Member map so it doesn't affect Guild#getMembers for the leave event.
        TLongSet memberIds = getMembersView().keySet(); // copies keys
        getJDA().getGuildCache().stream()
                .map(GuildImpl.class::cast)
                .forEach(g -> memberIds.removeAll(g.getMembersView().keySet()));
        // Remember, everything left in memberIds is removed from the userMap
        SnowflakeCacheViewImpl<User> userView = getJDA().getUsersView();
        try (UnlockHook hook = userView.writeLock())
        {
            long selfId = getJDA().getSelfUser().getIdLong();
            memberIds.forEach(memberId -> {
                if (memberId == selfId)
                    return true; // don't remove selfUser from cache
                userView.remove(memberId);
                getJDA().getEventCache().clear(EventCache.Type.USER, memberId);
                return true;
            });
        }
    }

    public void uncacheChannel(GuildChannel channel, boolean keepThreads)
    {
        long id = channel.getIdLong();

        // Enforce idempotence by checking the channel was in cache
        // If the channel was not in cache, there is no reason to cleanup anything else.
        // This idempotency makes sure that new cache is never affected by old cache
        if (channelCache.remove(channel.getType(), id) == null)
            return;

        api.getChannelsView().remove(channel.getType(), id);

        if (!keepThreads && channel instanceof IThreadContainer)
        {
            // Remove dangling threads
            SortedChannelCacheViewImpl<GuildChannel> localView = this.getChannelView();
            ChannelCacheViewImpl<Channel> globalView = api.getChannelsView();
            Predicate<ThreadChannel> predicate = thread -> channel.equals(thread.getParentChannel());

            try (UnlockHook hook1 = localView.writeLock(); UnlockHook hook2 = globalView.writeLock())
            {
                localView.removeIf(ThreadChannel.class, predicate);
                globalView.removeIf(ThreadChannel.class, predicate);
            }
        }
    }

    @Nonnull
    @Override
    public RestAction<List<Command>> retrieveCommands(boolean withLocalizations)
    {
        Route.CompiledRoute route = Route.Interactions.GET_GUILD_COMMANDS
                .compile(getJDA().getSelfUser().getApplicationId(), getId())
                .withQueryParams("with_localizations", String.valueOf(withLocalizations));

        return new RestActionImpl<>(getJDA(), route,
                (response, request) ->
                        response.getArray()
                                .stream(DataArray::getObject)
                                .map(json -> new CommandImpl(getJDA(), this, json))
                                .collect(Collectors.toList()));
    }

    @Nonnull
    @Override
    public RestAction<Command> retrieveCommandById(@Nonnull String id)
    {
        Checks.isSnowflake(id);
        Route.CompiledRoute route = Route.Interactions.GET_GUILD_COMMAND.compile(getJDA().getSelfUser().getApplicationId(), getId(), id);
        return new RestActionImpl<>(getJDA(), route, (response, request) -> new CommandImpl(getJDA(), this, response.getObject()));
    }

    @Nonnull
    @Override
    public CommandCreateAction upsertCommand(@Nonnull CommandData command)
    {
        Checks.notNull(command, "CommandData");
        return new CommandCreateActionImpl(this, (CommandDataImpl) command);
    }

    @Nonnull
    @Override
    public CommandListUpdateAction updateCommands()
    {
        Route.CompiledRoute route = Route.Interactions.UPDATE_GUILD_COMMANDS.compile(getJDA().getSelfUser().getApplicationId(), getId());
        return new CommandListUpdateActionImpl(getJDA(), this, route);
    }

    @Nonnull
    @Override
    public CommandEditAction editCommandById(@Nonnull Command.Type type, @Nonnull String id)
    {
        Checks.isSnowflake(id);
        Checks.notNull(type, "CommandType");
        Checks.check(type != Command.Type.UNKNOWN, "Type must not be UNKNOWN");
        return new CommandEditActionImpl(this, type, id);
    }

    @Nonnull
    @Override
    public RestAction<Void> deleteCommandById(@Nonnull String commandId)
    {
        Checks.isSnowflake(commandId);
        Route.CompiledRoute route = Route.Interactions.DELETE_GUILD_COMMAND.compile(getJDA().getSelfUser().getApplicationId(), getId(), commandId);
        return new RestActionImpl<>(getJDA(), route);
    }

    @Nonnull
    @Override
    public RestAction<List<IntegrationPrivilege>> retrieveIntegrationPrivilegesById(@Nonnull String targetId)
    {
        Checks.isSnowflake(targetId, "ID");
        Route.CompiledRoute route = Route.Interactions.GET_COMMAND_PERMISSIONS.compile(getJDA().getSelfUser().getApplicationId(), getId(), targetId);
        return new RestActionImpl<>(getJDA(), route, (response, request) -> parsePrivilegesList(response.getObject()));
    }

    @Nonnull
    @Override
    public RestAction<PrivilegeConfig> retrieveCommandPrivileges()
    {
        Route.CompiledRoute route = Route.Interactions.GET_ALL_COMMAND_PERMISSIONS.compile(getJDA().getSelfUser().getApplicationId(), getId());
        return new RestActionImpl<>(getJDA(), route, (response, request) -> {
            Map<String, List<IntegrationPrivilege>> privileges = new HashMap<>();
            response.getArray().stream(DataArray::getObject).forEach(obj -> {
                String id = obj.getString("id");
                List<IntegrationPrivilege> list = Collections.unmodifiableList(parsePrivilegesList(obj));
                privileges.put(id, list);
            });
            return new PrivilegeConfig(this, privileges);
        });
    }

    private List<IntegrationPrivilege> parsePrivilegesList(DataObject obj)
    {
        return obj.getArray("permissions")
                .stream(DataArray::getObject)
                .map(this::parsePrivilege)
                .collect(Collectors.toList());
    }

    private IntegrationPrivilege parsePrivilege(DataObject data)
    {
        IntegrationPrivilege.Type type = IntegrationPrivilege.Type.fromKey(data.getInt("type", 1));
        boolean enabled = data.getBoolean("permission");
        return new IntegrationPrivilege(this, type, enabled, data.getUnsignedLong("id"));
    }

    @Nonnull
    @Override
    public RestAction<EnumSet<Region>> retrieveRegions(boolean includeDeprecated)
    {
        Route.CompiledRoute route = Route.Guilds.GET_VOICE_REGIONS.compile(getId());
        return new RestActionImpl<>(getJDA(), route, (response, request) ->
        {
            EnumSet<Region> set = EnumSet.noneOf(Region.class);
            DataArray arr = response.getArray();
            for (int i = 0; i < arr.length(); i++)
            {
                DataObject obj = arr.getObject(i);
                if (!includeDeprecated && obj.getBoolean("deprecated"))
                    continue;
                String id = obj.getString("id", "");
                Region region = Region.fromKey(id);
                if (region != Region.UNKNOWN)
                    set.add(region);
            }
            return set;
        });
    }

    @Nonnull
    @Override
    public RestAction<List<AutoModRule>> retrieveAutoModRules()
    {
        checkPermission(Permission.MANAGE_SERVER);
        Route.CompiledRoute route = Route.AutoModeration.LIST_RULES.compile(getId());
        return new RestActionImpl<>(api, route, (response, request) ->
        {
            DataArray array = response.getArray();
            List<AutoModRule> rules = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++)
            {
                try
                {
                    DataObject obj = array.getObject(i);
                    rules.add(AutoModRuleImpl.fromData(this, obj));
                }
                catch (ParsingException exception)
                {
                    EntityBuilder.LOG.error("Failed to parse AutoModRule", exception);
                }
            }
            return Collections.unmodifiableList(rules);
        });
    }

    @Nonnull
    @Override
    public RestAction<AutoModRule> retrieveAutoModRuleById(@Nonnull String id)
    {
        Checks.isSnowflake(id);
        checkPermission(Permission.MANAGE_SERVER);
        Route.CompiledRoute route = Route.AutoModeration.GET_RULE.compile(getId(), id);
        return new RestActionImpl<>(api, route, (response, request) -> AutoModRuleImpl.fromData(this, response.getObject()));
    }

    @Nonnull
    @Override
    public AuditableRestAction<AutoModRule> createAutoModRule(@Nonnull AutoModRuleData rule)
    {
        Checks.notNull(rule, "AutoMod Rule");
        rule.getRequiredPermissions().forEach(this::checkPermission);
        Route.CompiledRoute route = Route.AutoModeration.CREATE_RULE.compile(getId());
        return new AuditableRestActionImpl<>(api, route, rule.toData(), (response, request) -> AutoModRuleImpl.fromData(this, response.getObject()));
    }

    @Nonnull
    @Override
    public AutoModRuleManager modifyAutoModRuleById(@Nonnull String id)
    {
        Checks.isSnowflake(id);
        checkPermission(Permission.MANAGE_SERVER);
        return new AutoModRuleManagerImpl(this, id);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> deleteAutoModRuleById(@Nonnull String id)
    {
        Checks.isSnowflake(id);
        checkPermission(Permission.MANAGE_SERVER);
        Route.CompiledRoute route = Route.AutoModeration.DELETE_RULE.compile(getId(), id);
        return new AuditableRestActionImpl<>(api, route);
    }

    @Nonnull
    @Override
    public MemberAction addMember(@Nonnull String accessToken, @Nonnull UserSnowflake user)
    {
        Checks.notBlank(accessToken, "Access-Token");
        Checks.notNull(user, "User");
        Checks.check(!isMember(user), "User is already in this guild");
        if (!getSelfMember().hasPermission(Permission.CREATE_INSTANT_INVITE))
            throw new InsufficientPermissionException(this, Permission.CREATE_INSTANT_INVITE);
        return new MemberActionImpl(getJDA(), this, user.getId(), accessToken);
    }

    @Override
    public boolean isLoaded()
    {
        // Only works with GUILD_MEMBERS intent
        return getJDA().isIntent(GatewayIntent.GUILD_MEMBERS)
                && (long) getMemberCount() <= getMemberCache().size();
    }

    @Override
    public void pruneMemberCache()
    {
        try (UnlockHook h = memberCache.writeLock())
        {
            EntityBuilder builder = getJDA().getEntityBuilder();
            Set<Member> members = memberCache.asSet();
            members.forEach(m -> builder.updateMemberCache((MemberImpl) m));
        }
    }

    @Override
    public boolean unloadMember(long userId)
    {
        if (userId == api.getSelfUser().getIdLong())
            return false;
        MemberImpl member = (MemberImpl) getMemberById(userId);
        if (member == null)
            return false;
        api.getEntityBuilder().updateMemberCache(member, true);
        return true;
    }

    @Override
    public int getMemberCount()
    {
        return memberCount;
    }

    @Nonnull
    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getIconId()
    {
        return iconId;
    }

    @Nonnull
    @Override
    public Set<String> getFeatures()
    {
        return features;
    }

    @Override
    public String getSplashId()
    {
        return splashId;
    }

    @Nullable
    @Override
    public String getVanityCode()
    {
        return vanityCode;
    }

    @Override
    @Nonnull
    public RestAction<VanityInvite> retrieveVanityInvite()
    {
        checkPermission(Permission.MANAGE_SERVER);
        JDAImpl api = getJDA();
        Route.CompiledRoute route = Route.Guilds.GET_VANITY_URL.compile(getId());
        return new RestActionImpl<>(api, route,
            (response, request) -> new VanityInvite(vanityCode, response.getObject().getInt("uses")));
    }

    @Nullable
    @Override
    public String getDescription()
    {
        return description;
    }

    @Nonnull
    @Override
    public DiscordLocale getLocale()
    {
        return preferredLocale;
    }

    @Nullable
    @Override
    public String getBannerId()
    {
        return banner;
    }

    @Nonnull
    @Override
    public BoostTier getBoostTier()
    {
        return boostTier;
    }

    @Override
    public int getBoostCount()
    {
        return boostCount;
    }

    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions") // can't be null here
    public List<Member> getBoosters()
    {
        return memberCache.applyStream((members) ->
            members.filter(m -> m.getTimeBoosted() != null)
                   .sorted(Comparator.comparing(Member::getTimeBoosted))
                   .collect(Helpers.toUnmodifiableList()));
    }

    @Override
    public int getMaxMembers()
    {
        return maxMembers;
    }

    @Override
    public int getMaxPresences()
    {
        return maxPresences;
    }

    @Nonnull
    @Override
    public RestAction<MetaData> retrieveMetaData()
    {
        Route.CompiledRoute route = Route.Guilds.GET_GUILD.compile(getId());
        route = route.withQueryParams("with_counts", "true");
        return new RestActionImpl<>(getJDA(), route, (response, request) -> {
            DataObject json = response.getObject();
            int memberLimit = json.getInt("max_members", 0);
            int presenceLimit = json.getInt("max_presences", 5000);
            this.maxMembers = memberLimit;
            this.maxPresences = presenceLimit;
            int approxMembers = json.getInt("approximate_member_count", this.memberCount);
            int approxPresence = json.getInt("approximate_presence_count", 0);
            return new MetaData(memberLimit, presenceLimit, approxPresence, approxMembers);
        });
    }

    @Override
    public VoiceChannel getAfkChannel()
    {
        return afkChannel;
    }

    @Override
    public TextChannel getSystemChannel()
    {
        return systemChannel;
    }

    @Override
    public TextChannel getRulesChannel()
    {
        return rulesChannel;
    }

    @Nonnull
    @Override
    public RestAction<List<ScheduledEvent>> retrieveScheduledEvents(boolean includeUserCount)
    {
        Route.CompiledRoute route = Route.Guilds.GET_SCHEDULED_EVENTS.compile(getId())
                .withQueryParams("with_user_count", String.valueOf(includeUserCount));

        EntityBuilder entityBuilder = getJDA().getEntityBuilder();
        return new RestActionImpl<>(getJDA(), route,
                (response, request) -> Helpers.mapGracefully(
                        response.getArray().stream(DataArray::getObject),
                        data -> entityBuilder.createScheduledEvent(this, data),
                        "Failed to parse scheduled event"
                ).collect(Helpers.toUnmodifiableList()));
    }

    @Nonnull
    @Override
    public CacheRestAction<ScheduledEvent> retrieveScheduledEventById(@Nonnull String id)
    {
        Checks.isSnowflake(id);
        return new DeferredRestAction<>(getJDA(), ScheduledEvent.class,
                () -> getScheduledEventById(id),
                () ->
                {
                    Route.CompiledRoute route = Route.Guilds.GET_SCHEDULED_EVENT.compile(getId(), id);
                    return new RestActionImpl<>(getJDA(), route, (response, request) -> api.getEntityBuilder().createScheduledEvent(this, response.getObject()));
                });
    }

    @Nonnull
    @Override
    public CacheRestAction<ScheduledEvent> retrieveScheduledEventById(long id)
    {
        return retrieveScheduledEventById(Long.toUnsignedString(id));
    }

    @Nonnull
    @Override
    public ScheduledEventAction createScheduledEvent(@Nonnull String name, @Nonnull String location, @Nonnull OffsetDateTime startTime, @Nonnull OffsetDateTime endTime)
    {
        checkPermission(Permission.MANAGE_EVENTS);
        return new ScheduledEventActionImpl(name, location, startTime, endTime, this);
    }

    @Nonnull
    @Override
    public ScheduledEventAction createScheduledEvent(@Nonnull String name, @Nonnull GuildChannel channel, @Nonnull OffsetDateTime startTime)
    {
        checkPermission(Permission.MANAGE_EVENTS);
        return new ScheduledEventActionImpl(name, channel, startTime, this);
    }


    @Override
    public TextChannel getCommunityUpdatesChannel()
    {
        return communityUpdatesChannel;
    }

    @Nullable
    @Override
    public TextChannel getSafetyAlertsChannel()
    {
        return safetyAlertsChannel;
    }

    @Nonnull
    @Override
    public RestAction<List<Webhook>> retrieveWebhooks()
    {
        if (!getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS))
            throw new InsufficientPermissionException(this, Permission.MANAGE_WEBHOOKS);

        Route.CompiledRoute route = Route.Guilds.GET_WEBHOOKS.compile(getId());

        return new RestActionImpl<>(getJDA(), route, (response, request) ->
        {
            DataArray array = response.getArray();
            List<Webhook> webhooks = new ArrayList<>(array.length());
            EntityBuilder builder = api.getEntityBuilder();

            for (int i = 0; i < array.length(); i++)
            {
                try
                {
                    webhooks.add(builder.createWebhook(array.getObject(i)));
                }
                catch (Exception e)
                {
                    JDAImpl.LOG.error("Error creating webhook from json", e);
                }
            }

            return Collections.unmodifiableList(webhooks);
        });
    }

    @Override
    public Member getOwner()
    {
        return owner;
    }

    @Override
    public long getOwnerIdLong()
    {
        return ownerId;
    }

    @Nonnull
    @Override
    public Timeout getAfkTimeout()
    {
        return afkTimeout;
    }

    @Nonnull
    @Override
    public SecurityIncidentActions getSecurityIncidentActions()
    {
        return securityIncidentActions;
    }

    @Nonnull
    @Override
    public SecurityIncidentDetections getSecurityIncidentDetections()
    {
        return securityIncidentDetections;
    }

    @Override
    public boolean isMember(@Nonnull UserSnowflake user)
    {
        return memberCache.get(user.getIdLong()) != null;
    }

    @Nonnull
    @Override
    public Member getSelfMember()
    {
        Member member = getMember(getJDA().getSelfUser());
        if (member == null)
            throw new IllegalStateException("Guild does not have a self member");
        return member;
    }

    @Override
    public Member getMember(@Nonnull UserSnowflake user)
    {
        Checks.notNull(user, "User");
        return getMemberById(user.getIdLong());
    }

    @Nonnull
    @Override
    public MemberCacheView getMemberCache()
    {
        return memberCache;
    }

    @Nonnull
    @Override
    public SortedSnowflakeCacheView<ScheduledEvent> getScheduledEventCache()
    {
        return scheduledEventCache;
    }

    @Nonnull
    @Override
    public SortedSnowflakeCacheView<Category> getCategoryCache()
    {
        return channelCache.ofType(Category.class);
    }

    @Nonnull
    @Override
    public SortedSnowflakeCacheView<TextChannel> getTextChannelCache()
    {
        return channelCache.ofType(TextChannel.class);
    }

    @Nonnull
    @Override
    public SortedSnowflakeCacheView<NewsChannel> getNewsChannelCache()
    {
        return channelCache.ofType(NewsChannel.class);
    }

    @Nonnull
    @Override
    public SortedSnowflakeCacheView<VoiceChannel> getVoiceChannelCache()
    {
        return channelCache.ofType(VoiceChannel.class);
    }

    @Nonnull
    @Override
    public SortedSnowflakeCacheView<ForumChannel> getForumChannelCache()
    {
        return channelCache.ofType(ForumChannel.class);
    }

    @Nonnull
    @Override
    public SnowflakeCacheView<MediaChannel> getMediaChannelCache()
    {
        return channelCache.ofType(MediaChannel.class);
    }

    @Nonnull
    @Override
    public SortedSnowflakeCacheView<StageChannel> getStageChannelCache()
    {
        return channelCache.ofType(StageChannel.class);
    }

    @Nonnull
    @Override
    public SortedSnowflakeCacheView<ThreadChannel> getThreadChannelCache()
    {
        return channelCache.ofType(ThreadChannel.class);
    }

    @Nonnull
    @Override
    public SortedChannelCacheViewImpl<GuildChannel> getChannelCache()
    {
        return channelCache;
    }

    @Nullable
    @Override
    public GuildChannel getGuildChannelById(long id)
    {
        return channelCache.getElementById(id);
    }

    @Override
    public GuildChannel getGuildChannelById(@Nonnull ChannelType type, long id)
    {
        return channelCache.getElementById(type, id);
    }

    @Nonnull
    @Override
    public SortedSnowflakeCacheView<Role> getRoleCache()
    {
        return roleCache;
    }

    @Nonnull
    @Override
    public SnowflakeCacheView<RichCustomEmoji> getEmojiCache()
    {
        return emojicache;
    }

    @Nonnull
    @Override
    public SnowflakeCacheView<GuildSticker> getStickerCache()
    {
        return stickerCache;
    }

    @Nonnull
    @Override
    public List<GuildChannel> getChannels(boolean includeHidden)
    {
        if (includeHidden)
        {
            return channelCache.applyStream(stream ->
                stream.filter(it -> !it.getType().isThread())
                      .sorted()
                      .collect(Helpers.toUnmodifiableList())
            );
        }

        // When we remove hidden channels there are 2 considerations to account for:
        //
        // 1. A channel is not visible if we don't have VIEW_CHANNEL permissions
        // 2. A category is not visible if we don't see any channels within it
        //
        // In our implementation we iterate all applicable channels and only add categories,
        // when a member of the category is added too.
        //
        // Note: We avoid using Category#getChannels because it would iterate the entire cache each time.
        // This is an optimization to avoid many unnecessary iterations.

        Member self = getSelfMember();

        SortedSet<GuildChannel> channels = new TreeSet<>();
        channelCache.ofType(ICategorizableChannel.class).forEachUnordered(channel ->
        {
            // Hide threads and inaccessible channels
            if (channel.getType().isThread() || !self.hasPermission(channel, Permission.VIEW_CHANNEL)) return;

            Category category = channel.getParentCategory();
            channels.add(channel);

            // Empty categories will never show up here,
            // since no categorizable channel will add them to this group
            if (category != null)
                channels.add(category);
        });

        return Collections.unmodifiableList(new ArrayList<>(channels));
    }

    @Nonnull
    @Override
    public RestAction<List<RichCustomEmoji>> retrieveEmojis()
    {
        Route.CompiledRoute route = Route.Emojis.GET_EMOJIS.compile(getId());
        return new RestActionImpl<>(getJDA(), route, (response, request) ->
        {
            EntityBuilder builder = GuildImpl.this.getJDA().getEntityBuilder();
            DataArray emojis = response.getArray();
            List<RichCustomEmoji> list = new ArrayList<>(emojis.length());
            for (int i = 0; i < emojis.length(); i++)
            {
                DataObject emoji = emojis.getObject(i);
                list.add(builder.createEmoji(GuildImpl.this, emoji));
            }

            return Collections.unmodifiableList(list);
        });
    }

    @Nonnull
    @Override
    public RestAction<RichCustomEmoji> retrieveEmojiById(@Nonnull String id)
    {
        Checks.isSnowflake(id, "Emoji ID");

        JDAImpl jda = getJDA();
        return new DeferredRestAction<>(jda, RichCustomEmoji.class,
        () -> {
            RichCustomEmoji emoji = getEmojiById(id);
            if (emoji != null)
            {
                if (emoji.getOwner() != null || !getSelfMember().hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS))
                    return emoji;
            }
            return null;
        }, () -> {
            Route.CompiledRoute route = Route.Emojis.GET_EMOJI.compile(getId(), id);
            return new AuditableRestActionImpl<>(jda, route, (response, request) ->
            {
                EntityBuilder builder = GuildImpl.this.getJDA().getEntityBuilder();
                return builder.createEmoji(GuildImpl.this, response.getObject());
            });
        });
    }

    @Nonnull
    @Override
    public RestAction<List<GuildSticker>> retrieveStickers()
    {
        Route.CompiledRoute route = Route.Stickers.GET_GUILD_STICKERS.compile(getId());
        return new RestActionImpl<>(getJDA(), route, (response, request) -> {
            DataArray array = response.getArray();
            List<GuildSticker> stickers = new ArrayList<>(array.length());
            EntityBuilder builder = api.getEntityBuilder();
            for (int i = 0; i < array.length(); i++)
            {
                DataObject object = null;
                try
                {
                    object = array.getObject(i);
                    GuildSticker sticker = (GuildSticker) builder.createRichSticker(object);
                    stickers.add(sticker);
                }
                catch (ParsingException | ClassCastException ex)
                {
                    EntityBuilder.LOG.error("Failed to parse sticker for JSON: {}", object, ex);
                }
            }

            return Collections.unmodifiableList(stickers);
        });
    }

    @Nonnull
    @Override
    public RestAction<GuildSticker> retrieveSticker(@Nonnull StickerSnowflake sticker)
    {
        Checks.notNull(sticker, "Sticker");
        Route.CompiledRoute route = Route.Stickers.GET_GUILD_STICKER.compile(getId(), sticker.getId());
        return new RestActionImpl<>(getJDA(), route, (response, request) -> {
            DataObject object = response.getObject();
            EntityBuilder builder = api.getEntityBuilder();
            return (GuildSticker) builder.createRichSticker(object);
        });
    }

    @Nonnull
    @Override
    public GuildStickerManager editSticker(@Nonnull StickerSnowflake sticker)
    {
        Checks.notNull(sticker, "Sticker");
        if (sticker instanceof GuildSticker)
            Checks.check(((GuildSticker) sticker).getGuildIdLong() == id, "Cannot edit a sticker from another guild!");
        Checks.check(!(sticker instanceof StandardSticker), "Cannot edit a standard sticker.");
        return new GuildStickerManagerImpl(this, id, sticker);
    }

    @Nonnull
    @Override
    public BanPaginationActionImpl retrieveBanList()
    {
        if (!getSelfMember().hasPermission(Permission.BAN_MEMBERS))
            throw new InsufficientPermissionException(this, Permission.BAN_MEMBERS);

        return new BanPaginationActionImpl(this);
    }

    @Nonnull
    @Override
    public RestAction<Ban> retrieveBan(@Nonnull UserSnowflake user)
    {
        if (!getSelfMember().hasPermission(Permission.BAN_MEMBERS))
            throw new InsufficientPermissionException(this, Permission.BAN_MEMBERS);

        Checks.notNull(user, "User");

        Route.CompiledRoute route = Route.Guilds.GET_BAN.compile(getId(), user.getId());
        return new RestActionImpl<>(getJDA(), route, (response, request) ->
        {
            EntityBuilder builder = api.getEntityBuilder();
            DataObject bannedObj = response.getObject();
            DataObject userJson = bannedObj.getObject("user");
            return new Ban(builder.createUser(userJson), bannedObj.getString("reason", null));
        });
    }

    @Nonnull
    @Override
    public RestAction<Integer> retrievePrunableMemberCount(int days)
    {
        if (!getSelfMember().hasPermission(Permission.KICK_MEMBERS))
            throw new InsufficientPermissionException(this, Permission.KICK_MEMBERS);

        Checks.check(days >= 1 && days <= 30, "Provided %d days must be between 1 and 30.", days);

        Route.CompiledRoute route = Route.Guilds.PRUNABLE_COUNT.compile(getId()).withQueryParams("days", Integer.toString(days));
        return new RestActionImpl<>(getJDA(), route, (response, request) -> response.getObject().getInt("pruned"));
    }

    @Nonnull
    @Override
    public Role getPublicRole()
    {
        return publicRole;
    }

    @Nullable
    @Override
    public DefaultGuildChannelUnion getDefaultChannel()
    {
        final Role role = getPublicRole();
        return (DefaultGuildChannelUnion) Stream.concat(getTextChannelCache().stream(), getNewsChannelCache().stream())
                .filter(c -> role.hasPermission(c, Permission.VIEW_CHANNEL))
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    @Nonnull
    @Override
    public GuildManager getManager()
    {
        return new GuildManagerImpl(this);
    }

    @Override
    public boolean isBoostProgressBarEnabled()
    {
        return boostProgressBarEnabled;
    }

    @Nonnull
    @Override
    public AuditLogPaginationAction retrieveAuditLogs()
    {
        return new AuditLogPaginationActionImpl(this);
    }

    @Nonnull
    @Override
    public RestAction<Void> leave()
    {
        if (getSelfMember().isOwner())
            throw new IllegalStateException("Cannot leave a guild that you are the owner of! Transfer guild ownership first!");

        Route.CompiledRoute route = Route.Self.LEAVE_GUILD.compile(getId());
        return new RestActionImpl<>(getJDA(), route);
    }

    @Nonnull
    @Override
    public RestAction<Void> delete()
    {
        if (!getJDA().getSelfUser().isBot() && getJDA().getSelfUser().isMfaEnabled())
            throw new IllegalStateException("Cannot delete a guild without providing MFA code. Use Guild#delete(String)");

        return delete(null);
    }

    @Nonnull
    @Override
    public RestAction<Void> delete(String mfaCode)
    {
        if (!getSelfMember().isOwner())
            throw new PermissionException("Cannot delete a guild that you do not own!");

        DataObject mfaBody = null;
        if (!getJDA().getSelfUser().isBot() && getJDA().getSelfUser().isMfaEnabled())
        {
            Checks.notEmpty(mfaCode, "Provided MultiFactor Auth code");
            mfaBody = DataObject.empty().put("code", mfaCode);
        }

        Route.CompiledRoute route = Route.Guilds.DELETE_GUILD.compile(getId());
        return new RestActionImpl<>(getJDA(), route, mfaBody);
    }

    @Nonnull
    @Override
    public AudioManager getAudioManager()
    {
        if (!getJDA().isIntent(GatewayIntent.GUILD_VOICE_STATES))
            throw new IllegalStateException("Cannot use audio features with disabled GUILD_VOICE_STATES intent!");
        final AbstractCacheView<AudioManager> managerMap = getJDA().getAudioManagersView();
        AudioManager mng = managerMap.get(id);
        if (mng == null)
        {
            // No previous manager found -> create one
            try (UnlockHook hook = managerMap.writeLock())
            {
                GuildImpl cachedGuild = (GuildImpl) getJDA().getGuildById(id);
                if (cachedGuild == null)
                    throw new IllegalStateException("Cannot get an AudioManager instance on an uncached Guild");
                mng = managerMap.get(id);
                if (mng == null)
                {
                    mng = new AudioManagerImpl(cachedGuild);
                    managerMap.getMap().put(id, mng);
                }
            }
        }
        return mng;
    }

    @Nonnull
    @Override
    public synchronized Task<Void> requestToSpeak()
    {
        if (!isRequestToSpeakPending())
            pendingRequestToSpeak = new CompletableFuture<>();

        Task<Void> task = new GatewayTask<>(pendingRequestToSpeak, this::cancelRequestToSpeak);
        updateRequestToSpeak();
        return task;
    }

    @Nonnull
    @Override
    public synchronized Task<Void> cancelRequestToSpeak()
    {
        if (isRequestToSpeakPending())
        {
            pendingRequestToSpeak.cancel(false);
            pendingRequestToSpeak = null;
        }

        AudioChannel channel = getSelfMember().getVoiceState().getChannel();
        if (channel instanceof StageChannel)
        {
            CompletableFuture<Void> future = ((StageChannel) channel).cancelRequestToSpeak().submit();
            return new GatewayTask<>(future, () -> future.cancel(false));
        }

        return new GatewayTask<>(CompletableFuture.completedFuture(null), () -> {});
    }

    @Nonnull
    @Override
    public JDAImpl getJDA()
    {
        return api;
    }

    @Nonnull
    @Override
    public List<GuildVoiceState> getVoiceStates()
    {
        return this.voiceStateCache.applyStream(stream ->
            stream.collect(Helpers.toUnmodifiableList())
        );
    }

    @Nonnull
    @Override
    @CheckReturnValue
    public CacheRestAction<GuildVoiceState> retrieveMemberVoiceStateById(long id)
    {
        JDAImpl jda = getJDA();
        Route.CompiledRoute route = Route.Guilds.GET_VOICE_STATE.compile(getId(), Long.toUnsignedString(id));
        return new DeferredRestAction<>(jda, GuildVoiceState.class,
                () -> voiceStateCache.get(id),
                () -> new RestActionImpl<>(jda, route, (response, request) ->
                {
                    EntityBuilder entityBuilder = jda.getEntityBuilder();
                    DataObject voiceStateData = response.getObject();
                    MemberImpl member = entityBuilder.createMember(this, voiceStateData.getObject("member"), null, null);
                    entityBuilder.updateMemberCache(member);
                    return entityBuilder.createGuildVoiceState(member, voiceStateData);
                }));
    }

    @Nonnull
    @Override
    public VerificationLevel getVerificationLevel()
    {
        return verificationLevel;
    }

    @Nonnull
    @Override
    public NotificationLevel getDefaultNotificationLevel()
    {
        return defaultNotificationLevel;
    }

    @Nonnull
    @Override
    public MFALevel getRequiredMFALevel()
    {
        return mfaLevel;
    }

    @Nonnull
    @Override
    public ExplicitContentLevel getExplicitContentLevel()
    {
        return explicitContentLevel;
    }

    @Nonnull
    @Override
    public Task<Void> loadMembers(@Nonnull Consumer<Member> callback)
    {
        Checks.notNull(callback, "Callback");
        if (!getJDA().isIntent(GatewayIntent.GUILD_MEMBERS))
            throw new IllegalStateException("Cannot use loadMembers without GatewayIntent.GUILD_MEMBERS!");
        if (isLoaded())
        {
            memberCache.forEachUnordered(callback);
            return new GatewayTask<>(CompletableFuture.completedFuture(null), () -> {});
        }

        MemberChunkManager chunkManager = getJDA().getClient().getChunkManager();
        boolean includePresences = getJDA().isIntent(GatewayIntent.GUILD_PRESENCES);
        MemberChunkManager.ChunkRequest handler = chunkManager.chunkGuild(this, includePresences, (last, list) -> list.forEach(callback));
        handler.exceptionally(ex -> {
            WebSocketClient.LOG.error("Encountered exception trying to handle member chunk response", ex);
            return null;
        });
        return new GatewayTask<>(handler, () -> handler.cancel(false)).onSetTimeout(handler::setTimeout);
    }

    @Nonnull
    @Override
    public CacheRestAction<Member> retrieveMemberById(long id)
    {
        JDAImpl jda = getJDA();
        return new DeferredRestAction<>(jda, Member.class,
                () -> getMemberById(id),
                () -> {
                    if (id == jda.getSelfUser().getIdLong())
                        return new CompletedRestAction<>(jda, getSelfMember());
                    Route.CompiledRoute route = Route.Guilds.GET_MEMBER.compile(getId(), Long.toUnsignedString(id));
                    return new RestActionImpl<>(jda, route, (resp, req) -> {
                        MemberImpl member = jda.getEntityBuilder().createMember(this, resp.getObject());
                        jda.getEntityBuilder().updateMemberCache(member);
                        return member;
                    });
                }).useCache(jda.isIntent(GatewayIntent.GUILD_MEMBERS));
    }

    @Nonnull
    @Override
    public Task<List<Member>> retrieveMembersByIds(boolean includePresence, @Nonnull long... ids)
    {
        Checks.notNull(ids, "ID Array");
        Checks.check(!includePresence || api.isIntent(GatewayIntent.GUILD_PRESENCES),
                "Cannot retrieve presences of members without GUILD_PRESENCES intent!");

        if (ids.length == 0)
            return new GatewayTask<>(CompletableFuture.completedFuture(Collections.emptyList()), () -> {});
        Checks.check(ids.length <= 100, "You can only request 100 members at once");
        MemberChunkManager chunkManager = api.getClient().getChunkManager();
        List<Member> collect = new ArrayList<>(ids.length);
        CompletableFuture<List<Member>> result = new CompletableFuture<>();
        MemberChunkManager.ChunkRequest handle = chunkManager.chunkGuild(this, includePresence, ids, (last, list) -> {
            collect.addAll(list);
            if (last)
                result.complete(collect);
        });

        handle.exceptionally(ex -> {
            WebSocketClient.LOG.error("Encountered exception trying to handle member chunk response", ex);
            result.completeExceptionally(ex);
            return null;
        });

        return new GatewayTask<>(result, () -> handle.cancel(false)).onSetTimeout(handle::setTimeout);
    }

    @Nonnull
    @Override
    @CheckReturnValue
    public Task<List<Member>> retrieveMembersByPrefix(@Nonnull String prefix, int limit)
    {
        Checks.notEmpty(prefix, "Prefix");
        Checks.positive(limit, "Limit");
        Checks.check(limit <= 100, "Limit must not be greater than 100");
        MemberChunkManager chunkManager = api.getClient().getChunkManager();

        List<Member> collect = new ArrayList<>(limit);
        CompletableFuture<List<Member>> result = new CompletableFuture<>();
        MemberChunkManager.ChunkRequest handle = chunkManager.chunkGuild(this, prefix, limit, (last, list) -> {
            collect.addAll(list);
            if (last)
                result.complete(collect);
        });

        handle.exceptionally(ex -> {
            WebSocketClient.LOG.error("Encountered exception trying to handle member chunk response", ex);
            result.completeExceptionally(ex);
            return null;
        });

        return new GatewayTask<>(result, () -> handle.cancel(false)).onSetTimeout(handle::setTimeout);
    }

    @Nonnull
    @Override
    public RestAction<List<ThreadChannel>> retrieveActiveThreads()
    {
        Route.CompiledRoute route = Route.Guilds.LIST_ACTIVE_THREADS.compile(getId());
        return new RestActionImpl<>(api, route, (response, request) ->
        {
            DataObject obj = response.getObject();
            DataArray selfThreadMembers = obj.getArray("members");
            DataArray threads = obj.getArray("threads");

            List<ThreadChannel> list = new ArrayList<>(threads.length());
            EntityBuilder builder = api.getEntityBuilder();

            TLongObjectMap<DataObject> selfThreadMemberMap = new TLongObjectHashMap<>();
            for (int i = 0; i < selfThreadMembers.length(); i++)
            {
                DataObject selfThreadMember = selfThreadMembers.getObject(i);

                //Store the thread member based on the "id" which is the _thread's_ id, not the member's id (which would be our id)
                selfThreadMemberMap.put(selfThreadMember.getLong("id"), selfThreadMember);
            }

            for (int i = 0; i < threads.length(); i++)
            {
                DataObject threadObj = threads.getObject(i);
                DataObject selfThreadMemberObj = selfThreadMemberMap.get(threadObj.getLong("id", 0));

                if (selfThreadMemberObj != null)
                {
                    //Combine the thread and self thread-member into a single object to model what we get from
                    // thread payloads (like from Gateway, etc)
                    threadObj.put("member", selfThreadMemberObj);
                }

                try
                {
                    ThreadChannel thread = builder.createThreadChannel(threadObj, this.getIdLong());
                    list.add(thread);
                }
                catch (Exception e)
                {
                    if (EntityBuilder.MISSING_CHANNEL.equals(e.getMessage()))
                        EntityBuilder.LOG.debug("Discarding thread without cached parent channel. JSON: {}", threadObj);
                    else
                        EntityBuilder.LOG.warn("Failed to create thread channel. JSON: {}", threadObj, e);
                }
            }

            return Collections.unmodifiableList(list);
        });
    }

    @Override
    public long getIdLong()
    {
        return id;
    }

    @Nonnull
    @Override
    public RestAction<List<Invite>> retrieveInvites()
    {
        if (!this.getSelfMember().hasPermission(Permission.MANAGE_SERVER))
            throw new InsufficientPermissionException(this, Permission.MANAGE_SERVER);

        final Route.CompiledRoute route = Route.Invites.GET_GUILD_INVITES.compile(getId());
        return new RestActionImpl<>(getJDA(), route, (response, request) ->
        {
            EntityBuilder entityBuilder = api.getEntityBuilder();
            DataArray array = response.getArray();
            List<Invite> invites = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++)
                invites.add(entityBuilder.createInvite(array.getObject(i)));
            return Collections.unmodifiableList(invites);
        });
    }

    @Nonnull
    @Override
    public RestAction<List<Template>> retrieveTemplates()
    {
        if (!this.getSelfMember().hasPermission(Permission.MANAGE_SERVER))
            throw new InsufficientPermissionException(this, Permission.MANAGE_SERVER);

        final Route.CompiledRoute route = Route.Templates.GET_GUILD_TEMPLATES.compile(getId());
        return new RestActionImpl<>(getJDA(), route, (response, request) ->
        {
            EntityBuilder entityBuilder = api.getEntityBuilder();
            DataArray array = response.getArray();
            List<Template> templates = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++)
            {
                try
                {
                    templates.add(entityBuilder.createTemplate(array.getObject(i)));
                }
                catch (Exception e)
                {
                    JDAImpl.LOG.error("Error creating template from json", e);
                }
            }
            return Collections.unmodifiableList(templates);
        });
    }

    @Nonnull
    @Override
    public RestAction<Template> createTemplate(@Nonnull String name, @Nullable String description)
    {
        checkPermission(Permission.MANAGE_SERVER);
        Checks.notBlank(name, "Name");
        name = name.trim();

        Checks.notLonger(name, 100, "Name");
        if (description != null)
            Checks.notLonger(description, 120, "Description");

        final Route.CompiledRoute route = Route.Templates.CREATE_TEMPLATE.compile(getId());

        DataObject object = DataObject.empty();
        object.put("name", name);
        object.put("description", description);

        return new RestActionImpl<>(getJDA(), route, object, (response, request) ->
        {
            EntityBuilder entityBuilder = api.getEntityBuilder();
            return entityBuilder.createTemplate(response.getObject());
        });
    }

    @Nonnull
    @Override
    public RestAction<GuildWelcomeScreen> retrieveWelcomeScreen()
    {
        final Route.CompiledRoute route = Route.Guilds.GET_WELCOME_SCREEN.compile(getId());
        return new RestActionImpl<>(getJDA(), route, (response, request) ->
        {
            EntityBuilder entityBuilder = api.getEntityBuilder();
            return entityBuilder.createWelcomeScreen(this, response.getObject());
        });
    }

    @Nonnull
    @Override
    public RestAction<Void> moveVoiceMember(@Nonnull UserSnowflake user, @Nullable AudioChannel audioChannel)
    {
        Checks.notNull(user, "User");
        Member member = user instanceof Member ? (Member) user : getMember(user);
        if (member != null)
            checkGuild(member.getGuild(), "Member");
        if (audioChannel != null)
            checkGuild(audioChannel.getGuild(), "AudioChannel");

        if (!getJDA().isCacheFlagSet(CacheFlag.VOICE_STATE))
            throw new IllegalStateException("Cannot move a Member with disabled CacheFlag.VOICE_STATE");
        GuildVoiceState vState = voiceStateCache.getElementById(user.getIdLong());
        if (vState == null)
            throw new IllegalStateException("You cannot move a Member who isn't in an AudioChannel!");
        // A cached voice state means that the member is connected to a channel, but we'll check just in case
        AudioChannel channel = vState.getChannel();
        if (channel == null)
            throw new IllegalStateException("You cannot move a Member who isn't in an AudioChannel!");

        Member selfMember = getSelfMember();
        if (!selfMember.hasPermission(channel, Permission.VOICE_MOVE_OTHERS))
            throw new InsufficientPermissionException(channel, Permission.VOICE_MOVE_OTHERS, "This account does not have Permission to MOVE_OTHERS out of the channel that the Member is currently in.");

        if (member != null)
        {
            if (audioChannel != null
                    && !selfMember.hasPermission(audioChannel, Permission.VOICE_CONNECT)
                    && !member.hasPermission(audioChannel, Permission.VOICE_CONNECT))
                throw new InsufficientPermissionException(audioChannel, Permission.VOICE_CONNECT,
                        "Neither this account nor the Member that is attempting to be moved have the VOICE_CONNECT permission " +
                                "for the destination AudioChannel, so the move cannot be done.");
        }

        DataObject body = DataObject.empty().put("channel_id", audioChannel == null ? null : audioChannel.getId());
        Route.CompiledRoute route = Route.Guilds.MODIFY_MEMBER.compile(getId(), user.getId());
        return new RestActionImpl<>(getJDA(), route, body);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> modifyNickname(@Nonnull Member member, String nickname)
    {
        Checks.notNull(member, "Member");
        checkGuild(member.getGuild(), "Member");

        if (member.equals(getSelfMember()))
        {
            if (!member.hasPermission(Permission.NICKNAME_CHANGE) && !member.hasPermission(Permission.NICKNAME_MANAGE))
                throw new InsufficientPermissionException(this, Permission.NICKNAME_CHANGE, "You neither have NICKNAME_CHANGE nor NICKNAME_MANAGE permission!");
        }
        else
        {
            checkPermission(Permission.NICKNAME_MANAGE);
            checkPosition(member);
        }

        JDAImpl jda = getJDA();
        return new DeferredRestAction<>(jda, () -> {
            DataObject body = DataObject.empty().put("nick", nickname == null ? "" : nickname);

            Route.CompiledRoute route;
            if (member.equals(getSelfMember()))
                route = Route.Guilds.MODIFY_SELF.compile(getId());
            else
                route = Route.Guilds.MODIFY_MEMBER.compile(getId(), member.getUser().getId());

            return new AuditableRestActionImpl<Void>(jda, route, body);
        }).setCacheCheck(() -> !Objects.equals(nickname, member.getNickname()));
    }

    @Nonnull
    @Override
    public AuditableRestAction<Integer> prune(int days, boolean wait, @Nonnull Role... roles)
    {
        checkPermission(Permission.KICK_MEMBERS);

        Checks.check(days >= 1 && days <= 30, "Provided %d days must be between 1 and 30.", days);
        Checks.notNull(roles, "Roles");

        Route.CompiledRoute route = Route.Guilds.PRUNE_MEMBERS.compile(getId());
        DataObject body = DataObject.empty();
        body.put("days", days);
        if (!wait)
            body.put("compute_prune_count", false);
        if (roles.length != 0)
        {
            for (Role role : roles)
            {
                Checks.notNull(role, "Role");
                Checks.check(role.getGuild().equals(this), "Role is not from the same guild!");
            }
            body.put("include_roles", Arrays.stream(roles).map(Role::getId).collect(Collectors.toList()));
        }
        return new AuditableRestActionImpl<>(getJDA(), route, body, (response, request) -> response.getObject().getInt("pruned", 0));
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> modifySecurityIncidents(@Nonnull SecurityIncidentActions incidents)
    {
        Checks.notNull(incidents, "SecurityIncidentActions");
        checkPermission(Permission.MANAGE_SERVER);

        Route.CompiledRoute route = Route.Guilds.MODIFY_GUILD_INCIDENTS.compile(getId());
        DataObject body = DataObject.empty()
                .put("invites_disabled_until", Objects.toString(incidents.getInvitesDisabledUntil(), null))
                .put("dms_disabled_until", Objects.toString(incidents.getDirectMessagesDisabledUntil(), null));
        return new AuditableRestActionImpl<>(api, route, body);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> kick(@Nonnull UserSnowflake user)
    {
        Checks.notNull(user, "User");
        checkPermission(Permission.KICK_MEMBERS);
        checkOwner(user.getIdLong(), "kick");
        checkPosition(user);

        Route.CompiledRoute route = Route.Guilds.KICK_MEMBER.compile(getId(), user.getId());
        return new AuditableRestActionImpl<>(getJDA(), route);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> ban(@Nonnull UserSnowflake user, int duration, @Nonnull TimeUnit unit)
    {
        Checks.notNull(user, "User");
        Checks.notNull(unit, "TimeUnit");
        Checks.notNegative(duration, "Deletion Timeframe");
        Checks.check(unit.toDays(duration) <= 7, "Deletion timeframe must not be larger than 7 days");
        checkPermission(Permission.BAN_MEMBERS);
        checkOwner(user.getIdLong(), "ban");
        checkPosition(user);

        Route.CompiledRoute route = Route.Guilds.BAN.compile(getId(), user.getId());
        DataObject params = DataObject.empty();

        if (duration > 0)
            params.put("delete_message_seconds", unit.toSeconds(duration));

        return new AuditableRestActionImpl<>(getJDA(), route, params);
    }

    @Nonnull
    @Override
    public AuditableRestAction<BulkBanResponse> ban(@Nonnull Collection<? extends UserSnowflake> users, @Nullable Duration deletionTime)
    {
        deletionTime = deletionTime == null ? Duration.ZERO : deletionTime;
        Checks.noneNull(users, "Users");
        Checks.notNegative(deletionTime.getSeconds(), "Deletion timeframe");
        Checks.check(deletionTime.getSeconds() <= TimeUnit.DAYS.toSeconds(7), "Deletion timeframe must not be larger than 7 days. Provided: %d seconds", deletionTime.getSeconds());
        Checks.check(users.size() <= 200, "Cannot ban more than 200 users at once");
        checkPermission(Permission.BAN_MEMBERS);
        checkPermission(Permission.MANAGE_SERVER);

        for (UserSnowflake user : users)
        {
            checkOwner(user.getIdLong(), "ban");
            checkPosition(user);
        }

        Set<Long> userIds = users.stream().map(UserSnowflake::getIdLong).collect(Collectors.toSet());
        DataObject body = DataObject.empty()
                .put("user_ids", DataArray.fromCollection(userIds))
                .put("delete_message_seconds", deletionTime.getSeconds());
        Route.CompiledRoute route = Route.Guilds.BULK_BAN.compile(getId());

        return new AuditableRestActionImpl<>(getJDA(), route, body, (res, req) -> {
            DataObject responseBody = res.getObject();
            List<UserSnowflake> bannedUsers = responseBody.getArray("banned_users")
                .stream(DataArray::getLong)
                .map(UserSnowflake::fromId)
                .collect(Collectors.toList());
            List<UserSnowflake> failedUsers = responseBody.getArray("failed_users")
                .stream(DataArray::getLong)
                .map(UserSnowflake::fromId)
                .collect(Collectors.toList());
            return new BulkBanResponse(bannedUsers, failedUsers);
        });
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> unban(@Nonnull UserSnowflake user)
    {
        Checks.notNull(user, "User");
        checkPermission(Permission.BAN_MEMBERS);

        Route.CompiledRoute route = Route.Guilds.UNBAN.compile(getId(), user.getId());
        return new AuditableRestActionImpl<>(getJDA(), route);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> timeoutUntil(@Nonnull UserSnowflake user, @Nonnull TemporalAccessor temporal)
    {
        Checks.notNull(user, "User");
        Checks.notNull(temporal, "Temporal");
        OffsetDateTime date = Helpers.toOffsetDateTime(temporal);
        Checks.check(date.isAfter(OffsetDateTime.now()), "Cannot put a member in time out with date in the past. Provided: %s", date);
        Checks.check(date.isBefore(OffsetDateTime.now().plusDays(Member.MAX_TIME_OUT_LENGTH)), "Cannot put a member in time out for more than 28 days. Provided: %s", date);
        checkPermission(Permission.MODERATE_MEMBERS);
        checkOwner(user.getIdLong(), "time out");
        checkPosition(user);

        return timeoutUntilById0(user.getId(), date);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> removeTimeout(@Nonnull UserSnowflake user)
    {
        Checks.notNull(user, "User");
        return timeoutUntilById0(user.getId(), null);
    }

    @Nonnull
    private AuditableRestAction<Void> timeoutUntilById0(@Nonnull String userId, @Nullable OffsetDateTime date)
    {
        DataObject body = DataObject.empty().put("communication_disabled_until", date == null ? null : date.toString());
        Route.CompiledRoute route = Route.Guilds.MODIFY_MEMBER.compile(getId(), userId);
        return new AuditableRestActionImpl<>(getJDA(), route, body);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> deafen(@Nonnull UserSnowflake user, boolean deafen)
    {
        Checks.notNull(user, "User");

        if (shouldCacheVoiceState(user.getIdLong()))
        {
            GuildVoiceStateImpl voiceState = voiceStateCache.get(user.getIdLong());
            AudioChannelUnion channel = voiceState != null ? voiceState.getChannel() : null;
            if (channel == null)
                throw new IllegalStateException("Can only deafen members who are currently in a voice channel");
            if (voiceState.isGuildDeafened() == deafen)
                return new CompletedRestAction<>(getJDA(), null);
            ((GuildChannelMixin<?>) channel).checkPermission(Permission.VOICE_DEAF_OTHERS);
        }

        DataObject body = DataObject.empty().put("deaf", deafen);
        Route.CompiledRoute route = Route.Guilds.MODIFY_MEMBER.compile(getId(), user.getId());
        return new AuditableRestActionImpl<>(getJDA(), route, body);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> mute(@Nonnull UserSnowflake user, boolean mute)
    {
        Checks.notNull(user, "User");

        if (shouldCacheVoiceState(user.getIdLong()))
        {
            GuildVoiceStateImpl voiceState = voiceStateCache.get(user.getIdLong());
            AudioChannelUnion channel = voiceState != null ? voiceState.getChannel() : null;
            if (channel == null)
                throw new IllegalStateException("Can only mute members who are currently in a voice channel");
            if (voiceState.isGuildMuted() == mute && (mute || !voiceState.isSuppressed()))
                return new CompletedRestAction<>(getJDA(), null);
            ((GuildChannelMixin<?>) channel).checkPermission(Permission.VOICE_MUTE_OTHERS);
        }

        DataObject body = DataObject.empty().put("mute", mute);
        Route.CompiledRoute route = Route.Guilds.MODIFY_MEMBER.compile(getId(), user.getId());
        return new AuditableRestActionImpl<>(getJDA(), route, body);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> addRoleToMember(@Nonnull UserSnowflake user, @Nonnull Role role)
    {
        Checks.notNull(user, "User");
        Checks.notNull(role, "Role");
        checkGuild(role.getGuild(), "Role");
        checkPermission(Permission.MANAGE_ROLES);
        checkPosition(role);

        Route.CompiledRoute route = Route.Guilds.ADD_MEMBER_ROLE.compile(getId(), user.getId(), role.getId());
        return new AuditableRestActionImpl<>(getJDA(), route);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> removeRoleFromMember(@Nonnull UserSnowflake user, @Nonnull Role role)
    {
        Checks.notNull(user, "User");
        Checks.notNull(role, "Role");
        checkGuild(role.getGuild(), "Role");
        checkPermission(Permission.MANAGE_ROLES);
        checkPosition(role);

        Route.CompiledRoute route = Route.Guilds.REMOVE_MEMBER_ROLE.compile(getId(), user.getId(), role.getId());
        return new AuditableRestActionImpl<>(getJDA(), route);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> modifyMemberRoles(@Nonnull Member member, Collection<Role> rolesToAdd, Collection<Role> rolesToRemove)
    {
        Checks.notNull(member, "Member");
        checkGuild(member.getGuild(), "Member");
        checkPermission(Permission.MANAGE_ROLES);
        Set<Role> currentRoles = new HashSet<>(((MemberImpl) member).getRoleSet());
        if (rolesToAdd != null)
        {
            checkRoles(rolesToAdd, "add", "to");
            currentRoles.addAll(rolesToAdd);
        }

        if (rolesToRemove != null)
        {
            checkRoles(rolesToRemove, "remove", "from");
            currentRoles.removeAll(rolesToRemove);
        }

        return modifyMemberRoles(member, currentRoles);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> modifyMemberRoles(@Nonnull Member member, @Nonnull Collection<Role> roles)
    {
        Checks.notNull(member, "Member");
        Checks.notNull(roles, "Roles");
        checkGuild(member.getGuild(), "Member");
        roles.forEach(role ->
        {
            Checks.notNull(role, "Role in collection");
            checkGuild(role.getGuild(), "Role: " + role.toString());
        });

        Checks.check(!roles.contains(getPublicRole()),
             "Cannot add the PublicRole of a Guild to a Member. All members have this role by default!");

        // Return an empty rest action if there were no changes
        final List<Role> memberRoles = member.getRoles();
        if (Helpers.deepEqualsUnordered(roles, memberRoles))
            return new CompletedRestAction<>(getJDA(), null);

        // Check removed roles
        for (Role r : memberRoles)
        {
            if (!roles.contains(r))
            {
                checkPosition(r);
                Checks.check(!r.isManaged(), "Cannot remove managed role from member. Role: %s", r);
            }
        }

        // Check added roles
        for (Role r : roles)
        {
            if (!memberRoles.contains(r))
            {
                checkPosition(r);
                Checks.check(!r.isManaged(), "Cannot add managed role to member. Role: %s", r);
            }
        }

        DataObject body = DataObject.empty()
            .put("roles", roles.stream().map(Role::getId).collect(Collectors.toSet()));
        Route.CompiledRoute route = Route.Guilds.MODIFY_MEMBER.compile(getId(), member.getUser().getId());

        return new AuditableRestActionImpl<>(getJDA(), route, body);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> transferOwnership(@Nonnull Member newOwner)
    {
        Checks.notNull(newOwner, "Member");
        checkGuild(newOwner.getGuild(), "Member");
        if (!getSelfMember().isOwner())
            throw new PermissionException("The logged in account must be the owner of this Guild to be able to transfer ownership");

        Checks.check(!getSelfMember().equals(newOwner),
                     "The member provided as the newOwner is the currently logged in account. Provide a different member to give ownership to.");

        Checks.check(!newOwner.getUser().isBot(), "Cannot transfer ownership of a Guild to a Bot!");

        DataObject body = DataObject.empty().put("owner_id", newOwner.getUser().getId());
        Route.CompiledRoute route = Route.Guilds.MODIFY_GUILD.compile(getId());
        return new AuditableRestActionImpl<>(getJDA(), route, body);
    }

    @Nonnull
    @Override
    public ChannelAction<TextChannel> createTextChannel(@Nonnull String name, Category parent)
    {
        return createChannel(ChannelType.TEXT, TextChannel.class, name, parent);
    }

    @Nonnull
    @Override
    public ChannelAction<NewsChannel> createNewsChannel(@Nonnull String name, Category parent)
    {
        return createChannel(ChannelType.NEWS, NewsChannel.class, name, parent);
    }

    @Nonnull
    @Override
    public ChannelAction<VoiceChannel> createVoiceChannel(@Nonnull String name, Category parent)
    {
        return createChannel(ChannelType.VOICE, VoiceChannel.class, name, parent);
    }

    @Nonnull
    @Override
    public ChannelAction<StageChannel> createStageChannel(@Nonnull String name, Category parent)
    {
        return createChannel(ChannelType.STAGE, StageChannel.class, name, parent);
    }

    @Nonnull
    @Override
    public ChannelAction<ForumChannel> createForumChannel(@Nonnull String name, Category parent)
    {
        return createChannel(ChannelType.FORUM, ForumChannel.class, name, parent);
    }

    @Nonnull
    @Override
    public ChannelAction<MediaChannel> createMediaChannel(@Nonnull String name, @Nullable Category parent)
    {
        return createChannel(ChannelType.MEDIA, MediaChannel.class, name, parent);
    }

    @Nonnull
    @Override
    public ChannelAction<Category> createCategory(@Nonnull String name)
    {
        return createChannel(ChannelType.CATEGORY, Category.class, name, null);
    }

    private <T extends GuildChannel> ChannelAction<T> createChannel(ChannelType type, Class<T> clazz, String name, Category parent)
    {
        checkCanCreateChannel(parent);
        Checks.notBlank(name, "Name");
        name = name.trim();
        Checks.notEmpty(name, "Name");
        Checks.notLonger(name, 100, "Name");
        return new ChannelActionImpl<>(clazz, name, this, type).setParent(parent);
    }

    @Nonnull
    @Override
    public RoleAction createRole()
    {
        checkPermission(Permission.MANAGE_ROLES);
        return new RoleActionImpl(this);
    }

    @Nonnull
    @Override
    public AuditableRestAction<RichCustomEmoji> createEmoji(@Nonnull String name, @Nonnull Icon icon, @Nonnull Role... roles)
    {
        checkPermission(Permission.MANAGE_GUILD_EXPRESSIONS);
        Checks.inRange(name, 2, CustomEmoji.EMOJI_NAME_MAX_LENGTH, "Emoji name");
        Checks.notNull(icon, "Emoji icon");
        Checks.notNull(roles, "Roles");

        DataObject body = DataObject.empty();
        body.put("name", name);
        body.put("image", icon.getEncoding());
        if (roles.length > 0) // making sure none of the provided roles are null before mapping them to the snowflake id
            body.put("roles", Stream.of(roles).filter(Objects::nonNull).map(ISnowflake::getId).collect(Collectors.toSet()));

        JDAImpl jda = getJDA();
        Route.CompiledRoute route = Route.Emojis.CREATE_EMOJI.compile(getId());
        return new AuditableRestActionImpl<>(jda, route, body, (response, request) ->
        {
            DataObject obj = response.getObject();
            return jda.getEntityBuilder().createEmoji(this, obj);
        });
    }

    @Nonnull
    @Override
    public AuditableRestAction<GuildSticker> createSticker(@Nonnull String name, @Nonnull String description, @Nonnull FileUpload file, @Nonnull Collection<String> tags)
    {
        checkPermission(Permission.MANAGE_GUILD_EXPRESSIONS);
        Checks.inRange(name, 2, 30, "Name");
        Checks.notNull(file, "File");
        Checks.notNull(description, "Description");
        Checks.notEmpty(tags, "Tags");
        if (!description.isEmpty())
            Checks.inRange(description, 2, 100, "Description");
        for (String t : tags)
            Checks.notEmpty(t, "Tags");

        String csv = String.join(",", tags);
        Checks.notLonger(csv, 200, "Tags");

        // Extract file extension and map to media type
        int index = file.getName().lastIndexOf('.');
        Checks.check(index > -1, "Filename for sticker is missing file extension. Provided: '" + file.getName() + "'. Must be PNG, GIF, or JSON.");

        // Convert file extension to media-type
        String extension = file.getName().substring(index + 1).toLowerCase(Locale.ROOT);
        MediaType mediaType;
        switch (extension)
        {
            case "apng":
            case "png":
                mediaType = Requester.MEDIA_TYPE_PNG;
                break;
            case "gif":
                mediaType = Requester.MEDIA_TYPE_GIF;
                break;
            case "json":
                mediaType = Requester.MEDIA_TYPE_JSON;
                break;
            default:
                throw new IllegalArgumentException("Unsupported file extension: '." + extension + "', must be PNG, GIF, or JSON.");
        }

        // Add sticker metadata as form parts (because payload_json is broken)
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        builder.addFormDataPart("name", name);
        builder.addFormDataPart("description", description);
        builder.addFormDataPart("tags", csv);

        // Attach file asset for sticker image/animation
        builder.addFormDataPart("file", file.getName(), file.getRequestBody(mediaType));

        MultipartBody body = builder.build();
        Route.CompiledRoute route = Route.Stickers.CREATE_GUILD_STICKER.compile(getId());
        return new AuditableRestActionImpl<>(api, route, body,
            (response, request) -> (GuildSticker) api.getEntityBuilder().createRichSticker(response.getObject())
        );
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> deleteSticker(@Nonnull StickerSnowflake id)
    {
        Checks.notNull(id, "Sticker");
        Route.CompiledRoute route = Route.Stickers.DELETE_GUILD_STICKER.compile(getId(), id.getId());
        return new AuditableRestActionImpl<>(api, route);
    }

    @Nonnull
    @Override
    public ChannelOrderAction modifyCategoryPositions()
    {
        return new ChannelOrderActionImpl(this, ChannelType.CATEGORY.getSortBucket());
    }

    @Nonnull
    @Override
    public ChannelOrderAction modifyTextChannelPositions()
    {
        return new ChannelOrderActionImpl(this, ChannelType.TEXT.getSortBucket());
    }

    @Nonnull
    @Override
    public ChannelOrderAction modifyVoiceChannelPositions()
    {
        return new ChannelOrderActionImpl(this, ChannelType.VOICE.getSortBucket());
    }

    @Nonnull
    @Override
    public CategoryOrderAction modifyTextChannelPositions(@Nonnull Category category)
    {
        Checks.notNull(category, "Category");
        checkGuild(category.getGuild(), "Category");
        return new CategoryOrderActionImpl(category, ChannelType.TEXT.getSortBucket());
    }

    @Nonnull
    @Override
    public CategoryOrderAction modifyVoiceChannelPositions(@Nonnull Category category)
    {
        Checks.notNull(category, "Category");
        checkGuild(category.getGuild(), "Category");
        return new CategoryOrderActionImpl(category, ChannelType.VOICE.getSortBucket());
    }

    @Nonnull
    @Override
    public RoleOrderAction modifyRolePositions(boolean useAscendingOrder)
    {
        return new RoleOrderActionImpl(this, useAscendingOrder);
    }

    @Nonnull
    @Override
    public GuildWelcomeScreenManager modifyWelcomeScreen()
    {
        return new GuildWelcomeScreenManagerImpl(this);
    }

    // ---- Checks ----

    protected void checkGuild(Guild providedGuild, String comment)
    {
        if (!equals(providedGuild))
            throw new IllegalArgumentException("Provided " + comment + " is not part of this Guild!");
    }

    protected void checkPermission(Permission perm)
    {
        if (!getSelfMember().hasPermission(perm))
            throw new InsufficientPermissionException(this, perm);
    }

    protected void checkPosition(UserSnowflake user)
    {
        Member member = resolveMember(user);
        if(member != null && !getSelfMember().canInteract(member))
            throw new HierarchyException("Can't modify a member with higher or equal highest role than yourself!");
    }

    protected void checkPosition(Role role)
    {
        if(!getSelfMember().canInteract(role))
            throw new HierarchyException("Can't modify a role with higher or equal highest role than yourself! Role: " + role.toString());
    }

    private void checkRoles(Collection<Role> roles, String type, String preposition)
    {
        roles.forEach(role ->
        {
            Checks.notNull(role, "Role in roles to " + type);
            checkGuild(role.getGuild(), "Role: " + role);
            checkPosition(role);
            Checks.check(!role.isManaged(), "Cannot %s a managed role %s a Member. Role: %s", type, preposition, role.toString());
        });
    }

    private void checkCanCreateChannel(Category parent)
    {
        if (parent != null)
        {
            Checks.check(parent.getGuild().equals(this), "Category is not from the same guild!");
            if (!getSelfMember().hasPermission(parent, Permission.MANAGE_CHANNEL))
                throw new InsufficientPermissionException(parent, Permission.MANAGE_CHANNEL);
        }
        else
        {
            checkPermission(Permission.MANAGE_CHANNEL);
        }
    }

    private void checkOwner(long userId, String what)
    {
        if (userId == ownerId)
            throw new HierarchyException("Cannot " + what + " the owner of a guild.");
    }

    private Member resolveMember(UserSnowflake user)
    {
        Member member = getMemberById(user.getIdLong());
        if (member == null && user instanceof Member)
        {
            member = (Member) user;
            // Only resolve if member is in the same guild, otherwise role information is not accurate
            if (!equals(member.getGuild()))
                member = null;
        }
        return member;
    }

    private synchronized boolean isRequestToSpeakPending()
    {
        return pendingRequestToSpeak != null && !pendingRequestToSpeak.isDone();
    }

    public synchronized void updateRequestToSpeak()
    {
        if (!isRequestToSpeakPending())
            return;
        AudioChannel connectedChannel = getSelfMember().getVoiceState().getChannel();
        if (!(connectedChannel instanceof StageChannel))
            return;
        StageChannel stage = (StageChannel) connectedChannel;
        CompletableFuture<Void> future = pendingRequestToSpeak;
        pendingRequestToSpeak = null;

        try
        {
            stage.requestToSpeak().queue((v) -> future.complete(null), future::completeExceptionally);
        }
        catch (Throwable ex)
        {
            future.completeExceptionally(ex);
            if (ex instanceof Error)
                throw ex;
        }
    }

    // ---- Setters -----

    public GuildImpl setOwner(Member owner)
    {
        // Only cache owner if user cache is enabled
        if (owner != null && getMemberById(owner.getIdLong()) != null)
            this.owner = owner;
        return this;
    }

    public GuildImpl setName(String name)
    {
        this.name = name;
        return this;
    }

    public GuildImpl setIconId(String iconId)
    {
        this.iconId = iconId;
        return this;
    }

    public GuildImpl setFeatures(Set<String> features)
    {
        this.features = Collections.unmodifiableSet(features);
        return this;
    }

    public GuildImpl setSplashId(String splashId)
    {
        this.splashId = splashId;
        return this;
    }

    public GuildImpl setVanityCode(String code)
    {
        this.vanityCode = code;
        return this;
    }

    public GuildImpl setDescription(String description)
    {
        this.description = description;
        return this;
    }

    public GuildImpl setBannerId(String bannerId)
    {
        this.banner = bannerId;
        return this;
    }

    public GuildImpl setMaxPresences(int maxPresences)
    {
        this.maxPresences = maxPresences;
        return this;
    }

    public GuildImpl setMaxMembers(int maxMembers)
    {
        this.maxMembers = maxMembers;
        return this;
    }

    public GuildImpl setAfkChannel(VoiceChannel afkChannel)
    {
        this.afkChannel = afkChannel;
        return this;
    }

    public GuildImpl setSystemChannel(TextChannel systemChannel)
    {
        this.systemChannel = systemChannel;
        return this;
    }

    public GuildImpl setRulesChannel(TextChannel rulesChannel)
    {
        this.rulesChannel = rulesChannel;
        return this;
    }

    public GuildImpl setCommunityUpdatesChannel(TextChannel communityUpdatesChannel)
    {
        this.communityUpdatesChannel = communityUpdatesChannel;
        return this;
    }

    public GuildImpl setSafetyAlertsChannel(TextChannel safetyAlertsChannel)
    {
        this.safetyAlertsChannel = safetyAlertsChannel;
        return this;
    }

    public GuildImpl setPublicRole(Role publicRole)
    {
        this.publicRole = publicRole;
        return this;
    }

    public GuildImpl setSecurityIncidentActions(SecurityIncidentActions actions)
    {
        this.securityIncidentActions = actions == null ? SecurityIncidentActions.disabled() : actions;
        return this;
    }

    public GuildImpl setSecurityIncidentDetections(SecurityIncidentDetections detections)
    {
        this.securityIncidentDetections = detections == null ? SecurityIncidentDetections.EMPTY : detections;
        return this;
    }

    public GuildImpl setVerificationLevel(VerificationLevel level)
    {
        this.verificationLevel = level;
        return this;
    }

    public GuildImpl setDefaultNotificationLevel(NotificationLevel level)
    {
        this.defaultNotificationLevel = level;
        return this;
    }

    public GuildImpl setRequiredMFALevel(MFALevel level)
    {
        this.mfaLevel = level;
        return this;
    }

    public GuildImpl setExplicitContentLevel(ExplicitContentLevel level)
    {
        this.explicitContentLevel = level;
        return this;
    }

    public GuildImpl setAfkTimeout(Timeout afkTimeout)
    {
        this.afkTimeout = afkTimeout;
        return this;
    }

    public GuildImpl setLocale(DiscordLocale locale)
    {
        this.preferredLocale = locale;
        return this;
    }

    public GuildImpl setBoostTier(int tier)
    {
        this.boostTier = BoostTier.fromKey(tier);
        return this;
    }

    public GuildImpl setBoostCount(int count)
    {
        this.boostCount = count;
        return this;
    }

    public GuildImpl setOwnerId(long ownerId)
    {
        this.ownerId = ownerId;
        return this;
    }

    public GuildImpl setMemberCount(int count)
    {
        this.memberCount = count;
        return this;
    }

    public GuildImpl setNSFWLevel(NSFWLevel nsfwLevel)
    {
        this.nsfwLevel = nsfwLevel;
        return this;
    }

    public GuildImpl setBoostProgressBarEnabled(boolean enabled)
    {
        this.boostProgressBarEnabled = enabled;
        return this;
    }

    // -- Map getters --

    public SortedSnowflakeCacheViewImpl<ScheduledEvent> getScheduledEventsView()
    {
        return scheduledEventCache;
    }

    public SortedChannelCacheViewImpl<GuildChannel> getChannelView()
    {
        return channelCache;
    }

    public SortedSnowflakeCacheViewImpl<Role> getRolesView()
    {
        return roleCache;
    }

    public SnowflakeCacheViewImpl<RichCustomEmoji> getEmojisView()
    {
        return emojicache;
    }

    public SnowflakeCacheViewImpl<GuildSticker> getStickersView()
    {
        return stickerCache;
    }

    public MemberCacheViewImpl getMembersView()
    {
        return memberCache;
    }

    @Nonnull
    @Override
    public NSFWLevel getNSFWLevel()
    {
        return nsfwLevel;
    }

    @Nullable
    public CacheView.SimpleCacheView<MemberPresenceImpl> getPresenceView()
    {
        return memberPresences;
    }

    @Nonnull
    public SnowflakeCacheViewImpl<GuildVoiceStateImpl> getVoiceStateView()
    {
        return this.voiceStateCache;
    }

    // -- Member Tracking --

    public void onMemberAdd()
    {
        memberCount++;
    }

    public void onMemberRemove(long memberId)
    {
        memberCount--;
        this.voiceStateCache.remove(memberId);
        if (this.memberPresences != null)
            this.memberPresences.remove(memberId);
    }

    // -- Voice State Cache Handling --

    public boolean shouldCacheVoiceState(long userId)
    {
        return userId == api.getSelfUser().getIdLong() || api.getCacheFlags().contains(CacheFlag.VOICE_STATE);
    }

    public GuildVoiceStateImpl getVoiceState(Member member)
    {
        GuildVoiceStateImpl voiceState = this.voiceStateCache.getElementById(member.getIdLong());
        if (voiceState != null)
            return voiceState;
        if (shouldCacheVoiceState(member.getIdLong()))
            return new GuildVoiceStateImpl(member);
        return null;
    }

    public void updateCacheVoiceStateMember(MemberImpl member)
    {
        if (!shouldCacheVoiceState(member.getIdLong()))
            return;

        try (UnlockHook hook = this.voiceStateCache.writeLock())
        {
            GuildVoiceStateImpl voiceState = this.voiceStateCache.get(member.getIdLong());
            if (voiceState != null)
                voiceState.setMember(member);
        }
    }

    public void handleVoiceStateUpdate(GuildVoiceStateImpl voiceState)
    {
        if (!shouldCacheVoiceState(voiceState.getIdLong()))
            return;

        try (UnlockHook hook = this.voiceStateCache.writeLock())
        {
            if (voiceState.getChannel() != null)
                this.voiceStateCache.getMap().put(voiceState.getIdLong(), voiceState);
            else
                this.voiceStateCache.getMap().remove(voiceState.getIdLong());
        }
    }

    public List<Member> getConnectedMembers(GuildChannel channel)
    {
        return this.voiceStateCache.applyStream(stream ->
            stream
                .filter(state -> channel.equals(state.getChannel()))
                .map(GuildVoiceStateImpl::getMember)
                .filter(Objects::nonNull) // sanity filter
                .collect(Helpers.toUnmodifiableList())
        );
    }

    // -- Object overrides --

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
            return true;
        if (!(o instanceof GuildImpl))
            return false;
        GuildImpl oGuild = (GuildImpl) o;
        return this.id == oGuild.id;
    }

    @Override
    public int hashCode()
    {
        return Long.hashCode(id);
    }

    @Override
    public String toString()
    {
        return new EntityString(this)
                .setName(getName())
                .toString();
    }
}
