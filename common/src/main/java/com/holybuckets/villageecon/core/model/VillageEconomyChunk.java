package com.holybuckets.villageecon.core.model;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.foundation.biome.BiomeAPI;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.foundation.modelInterface.IMangedChunkData;
import com.holybuckets.foundation.structure.StructureInfo;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.config.model.EconomyResource.ResourceType;
import com.holybuckets.villageecon.config.model.VillagePersonality;
import com.holybuckets.villageecon.core.VillageManager;
import com.holybuckets.villageecon.entity.MayorEntity;
import com.holybuckets.villageecon.entity.ModEntities;
import net.blay09.mods.balm.api.event.ChunkLoadingEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 *
 * Represents the economy of a single village, based on the chunk
 * where the origin of the village is. Needs to keep track of mayor,
 * if the mayor dies, this is source of truth.
 *
 * Holds only the IMMUTABLE village data that cannot be lost even if the Mayor dies:
 * the mayor's UUID, the village level, and the permanent "personalityModifier" and
 * "biomeModifier" (both instances of VillagePersonality), persisted to the chunk
 * as state ids. All dynamic economy data lives on the Mayor.
 */
public class VillageEconomyChunk implements IMangedChunkData {

    public static final String CLASS_ID = "013";
    private static final String NBT_KEY_HEADER = "villageEconomy";
    private static final Random RANDOM = new Random();

    public static ModConfig MOD_CONFIG;

    static final VillageEconomyChunk DEFAULT = new VillageEconomyChunk();
    static final String DEFAULT_ID = "DEFAULT";

    public static final VillagePersonality NEUTRAL = new VillagePersonality("neutral");

    public static void registerManagedChunkData() {
        ManagedChunk.registerManagedChunkData(VillageEconomyChunk.class, () -> new VillageEconomyChunk());
    }

    /** Variables **/
    private ServerLevel level;
    private String id;
    private ChunkPos pos;
    private ResourceLocation structureLoc;      //structure this village was created from
    private BlockPos origin;                    //origin of the village structure

    //Immutable village data - survives mayor death
    private UUID mayorId;                       //UUID of the Mayor Villager entity
    private int villageLevel;
    private String personalityModifierId;       //persisted state id
    private String biomeModifierId;             //persisted state id
    private List<String> luxuryResourceIds;     //luxuries assigned to this village, permanent

    //Hydrated state - both instances of the same modifier class
    private VillagePersonality personalityModifier;
    private VillagePersonality biomeModifier;

    private Mayor mayor;


    /** Constructors **/

    /** Default constructor - creates dummy node for deserialization **/
    private VillageEconomyChunk() {
        super();
        this.id = DEFAULT_ID;
        this.villageLevel = 1;
        this.luxuryResourceIds = new ArrayList<>();
        this.personalityModifier = NEUTRAL;
        this.biomeModifier = NEUTRAL;
    }

    /** Creates a new village economy for a freshly loaded village structure **/
    public VillageEconomyChunk(ServerLevel level, StructureInfo info) {
        this();
        this.level = level;
        this.origin = info.getOrigin();
        this.pos = new ChunkPos(origin);
        this.id = ChunkUtil.getId(pos);
        this.structureLoc = info.getStructureLocation();

        setModifiers();
        determineLuxuryResources();
    }

    /** Designates a chunk as a village without a source structure, ie a placed Mayor **/
    public VillageEconomyChunk(ServerLevel level, BlockPos origin) {
        this();
        this.level = level;
        this.origin = origin;
        this.pos = new ChunkPos(origin);
        this.id = ChunkUtil.getId(pos);
        this.structureLoc = null;

        setModifiers();
        determineLuxuryResources();
    }


    /** Getters **/

    public String getId() { return id; }

    public ChunkPos getChunkPos() { return pos; }

    public ServerLevel getLevel() { return level; }

    public BlockPos getOrigin() { return origin; }

    public ResourceLocation getStructureLoc() { return structureLoc; }

    public UUID getMayorId() { return mayorId; }

    public int getVillageLevel() { return villageLevel; }

    public VillagePersonality getPersonalityModifier() { return personalityModifier; }

    public VillagePersonality getBiomeModifier() { return biomeModifier; }

    public List<String> getLuxuryResourceIds() { return luxuryResourceIds; }

    /** Chunk radius in which this village can reach sellers on the market; grows with village level **/
    public int getBuyRadius() {
        int perLevel = ModConfig.getBalmConfig().tradeConfigs.buyRadiusPerLevelChunks;
        return Math.max(1, villageLevel) * perLevel;
    }

    @Nullable
    public ResourceLocation getBiome() {
        if (level == null || origin == null) return null;
        return BiomeAPI.get(level).nearestBiomes(origin, 1).get(0).getId();
    }

    public List<EconomyResource> getActiveResources()
    {
        List<EconomyResource> active = new ArrayList<>(MOD_CONFIG.getResources(ResourceType.STAPLE));

        if (villageLevel >= MOD_CONFIG.getEconomyConfig().getBasicResourceStartLevel())
            active.addAll(MOD_CONFIG.getResources(ResourceType.BASIC));

        if (villageLevel >= MOD_CONFIG.getEconomyConfig().getLuxuryResourceStartLevel()) {
            for (String luxuryId : luxuryResourceIds) {
                EconomyResource luxury = MOD_CONFIG.getResource(luxuryId);
                if (luxury != null) active.add(luxury);
            }
        }
        return active;
    }


    /** Setters **/

    @Override
    public void setId(String id) {
        if (id == null) return;
        this.id = id;
        this.pos = ChunkUtil.getChunkPos(id);
    }

    @Override
    public void setLevel(LevelAccessor levelAccessor) {
        if (levelAccessor == null || levelAccessor.isClientSide()) return;
        this.level = (ServerLevel) levelAccessor;
    }

    public void setVillageLevel(int villageLevel) {
        this.villageLevel = villageLevel;
    }


    //** CORE

    /**
     * Assigns the permanent state variables "personalityModifier" and "biomeModifier".
     * Both are instances of the same modifier class and are persisted as state ids.
     */
    private void setModifiers()
    {
        ResourceLocation biome = getBiome();

        List<VillagePersonality> temperments = MOD_CONFIG.getTempermentPersonalities();
        if (temperments.isEmpty()) {
            this.personalityModifier = NEUTRAL;
        } else {
            this.personalityModifier = temperments.get(RANDOM.nextInt(temperments.size()));
        }
        this.personalityModifierId = personalityModifier.getId();

        VillagePersonality biomePersonality = MOD_CONFIG.getBiomePersonality(biome);
        this.biomeModifier = (biomePersonality != null) ? biomePersonality : NEUTRAL;
        this.biomeModifierId = this.biomeModifier.getId();
    }

    private void determineLuxuryResources()
    {
        List<EconomyResource> pool = new ArrayList<>(MOD_CONFIG.getLuxuriesForBiome(getBiome()));
        int count = Math.min(MOD_CONFIG.getEconomyConfig().getAssignedLuxuryResourceCount(), pool.size());

        for (int i = 0; i < count; i++)
        {
            int totalWeight = pool.stream().mapToInt(EconomyResource::getWeight).sum();
            if (totalWeight <= 0) break;
            int roll = RANDOM.nextInt(totalWeight);
            for (EconomyResource luxury : pool) {
                roll -= luxury.getWeight();
                if (roll < 0) {
                    luxuryResourceIds.add(luxury.getResourceId());
                    pool.remove(luxury);
                    break;
                }
            }
        }
    }


    public Mayor createMayor()
    {
        if (level == null || origin == null) return null;

        VillageManager manager = VillageManager.get(level);
        if (manager == null) return null;

        MayorEntity mayorEntity = ModEntities.mayor.get().create(level);
        if (mayorEntity == null) return null;

        Mayor existing = manager.getMayor(pos);
        this.mayor = (existing != null) ? existing : new Mayor(level, this);

        mayorEntity.moveTo(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0, 0);
        mayorEntity.setCustomName(Component.literal("Mayor"));
        level.addFreshEntity(mayorEntity);

        this.mayorId = mayorEntity.getUUID();
        manager.registerMayor(mayor);

        LoggerProject.logInfo(CLASS_ID + "001", "Mayor created for village " + id
            + " with personality '" + personalityModifierId + "' and biome modifier '" + biomeModifierId + "'");
        return mayor;
    }

    /**
     * Binds an already spawned Mayor entity to this village, reusing the RAM resident
     * Mayor when one exists. Used when a Mayor is placed rather than spawned by us.
     */
    public Mayor adoptMayor(MayorEntity mayorEntity)
    {
        if (level == null || mayorEntity == null) return null;

        VillageManager manager = VillageManager.get(level);
        if (manager == null) return null;

        Mayor existing = manager.getMayor(pos);
        this.mayor = (existing != null) ? existing : new Mayor(level, this);
        this.mayor.attachEntity(mayorEntity);

        mayorEntity.setVillageChunkId(this.id);
        this.mayorId = mayorEntity.getUUID();
        manager.registerMayor(this.mayor);

        LoggerProject.logInfo(CLASS_ID + "002", "Mayor adopted for village " + id
            + " with personality '" + personalityModifierId + "' and biome modifier '" + biomeModifierId + "'");
        return this.mayor;
    }

    @Nullable
    public Mayor getMayor()
    {
        if (level == null) return null;
        VillageManager manager = VillageManager.get(level);
        if (manager == null) return null;
        this.mayor = manager.getMayor(pos);
        return mayor;
    }


    /** Static Methods **/

    public static VillageEconomyChunk getInstance(LevelAccessor levelAcc, String id) {
        VillageManager manager = VillageManager.get(levelAcc);
        if (manager == null) return null;
        return manager.getVillages().get(ChunkUtil.getChunkPos(id));
    }


    /** IMangedChunkData Overrides **/

    @Override
    public VillageEconomyChunk resolveSubData(LevelAccessor level, String id, @Nullable IMangedChunkData data)
    {
        if (id == null || level == null) return null;
        VillageEconomyChunk subData = (VillageEconomyChunk) data;
        if (subData != null && subData.structureLoc != null) {
            //good, always prioritize serialized data
        } else {
            subData = VillageEconomyChunk.getInstance(level, id);
        }
        if (subData != null)
            VillageManager.addVillage((ServerLevel) level, subData);

        return subData;
    }

    @Override
    public boolean isInit(String subClass) {
        return subClass.equals(VillageEconomyChunk.class.getName()) && this.id != null && !this.id.equals(DEFAULT_ID);
    }

    @Override
    public void handleChunkLoaded(ChunkLoadingEvent.Load event) {
        if (this.id.equals(DEFAULT_ID)) return;
        this.level = (ServerLevel) event.getLevel();
    }

    @Override
    public void handleChunkUnloaded(ChunkLoadingEvent.Unload event) {
        if (this.id.equals(DEFAULT_ID)) return;
    }


    /** Serialization **/

    @Override
    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();
        if (this.id.equals(DEFAULT_ID)) return tag;

        tag.putString("id", this.id);
        tag.putInt("villageLevel", this.villageLevel);
        if (structureLoc != null) tag.putString("structure", structureLoc.toString());
        if (origin != null) tag.putString("origin", HBUtil.BlockUtil.positionToString(origin));
        if (mayorId != null) tag.putUUID("mayorId", mayorId);

        //Persist modifiers as state ids
        if (personalityModifierId != null) tag.putString("personalityModifier", personalityModifierId);
        if (biomeModifierId != null) tag.putString("biomeModifier", biomeModifierId);
        tag.putString("luxuryResources", String.join(",", luxuryResourceIds));

        //NOTE: the Mayor's dynamic data is NOT stored here - it is owned and
        //persisted by the MayorEntity's own compound tag

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag)
    {
        if (tag == null || tag.isEmpty()) return;

        this.id = tag.getString("id");
        this.pos = ChunkUtil.getChunkPos(this.id);
        this.villageLevel = tag.getInt("villageLevel");

        if (tag.contains("structure"))
            this.structureLoc = new ResourceLocation(tag.getString("structure"));
        if (tag.contains("origin"))
            this.origin = HBUtil.BlockUtil.stringToBlockPos(tag.getString("origin"));
        if (tag.hasUUID("mayorId"))
            this.mayorId = tag.getUUID("mayorId");

        //Hydrate modifiers from persisted state ids
        this.personalityModifierId = tag.getString("personalityModifier");
        VillagePersonality personality = (MOD_CONFIG != null)
            ? MOD_CONFIG.getEconomyConfig().getPersonality(personalityModifierId) : null;
        this.personalityModifier = (personality != null) ? personality : NEUTRAL;

        this.biomeModifierId = tag.getString("biomeModifier");
        this.biomeModifier = NEUTRAL;   //TODO: hydrate real biome modifier from biomeModifierId

        this.luxuryResourceIds = new ArrayList<>();
        String luxuries = tag.getString("luxuryResources");
        if (!luxuries.isBlank()) {
            for (String luxuryId : luxuries.split(","))
                luxuryResourceIds.add(luxuryId.trim());
        }
    }
}
