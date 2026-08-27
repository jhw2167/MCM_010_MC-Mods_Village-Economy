package com.holybuckets.villageecon.core.model;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.foundation.modelInterface.IMangedChunkData;
import com.holybuckets.foundation.structure.StructureInfo;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.config.model.EconomyResource.ResourceType;
import com.holybuckets.villageecon.config.model.VillagePersonality;
import com.holybuckets.villageecon.core.VillageManager;
import net.blay09.mods.balm.api.event.ChunkLoadingEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Class: VillageEconomy
 * Description: Represents a single chunk holding the origin of a structure treated as a
 * village. Created when HBs Foundation emits a StructureLoadedEvent for a configured
 * village structure.
 *
 * Holds only the IMMUTABLE village data that cannot be lost even if the Mayor dies:
 * the mayor's UUID, the village level, and the permanent "personalityModifier" and
 * "biomeModifier" (both instances of VillagePersonality), persisted to the chunk
 * as state ids. All dynamic economy data lives on the Mayor.
 */
public class VillageEconomy implements IMangedChunkData {

    public static final String CLASS_ID = "013";
    private static final String NBT_KEY_HEADER = "villageEconomy";
    private static final Random RANDOM = new Random();

    public static ModConfig MOD_CONFIG;

    static final VillageEconomy DEFAULT = new VillageEconomy();
    static final String DEFAULT_ID = "DEFAULT";

    /** Neutral modifier applied when no configured modifier is eligible or resolvable **/
    public static final VillagePersonality NEUTRAL = new VillagePersonality("neutral");

    public static void registerManagedChunkData() {
        ManagedChunk.registerManagedChunkData(VillageEconomy.class, () -> new VillageEconomy());
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

    //Runtime
    private Mayor mayor;
    private CompoundTag pendingMayorData;       //deserialized mayor data awaiting mayor construction


    /** Constructors **/

    /** Default constructor - creates dummy node for deserialization **/
    private VillageEconomy() {
        super();
        this.id = DEFAULT_ID;
        this.villageLevel = 1;
        this.luxuryResourceIds = new ArrayList<>();
        this.personalityModifier = NEUTRAL;
        this.biomeModifier = NEUTRAL;
    }

    /** Creates a new village economy for a freshly loaded village structure **/
    public VillageEconomy(ServerLevel level, StructureInfo info) {
        this();
        this.level = level;
        this.origin = info.getOrigin();
        this.pos = new ChunkPos(origin);
        this.id = ChunkUtil.getId(pos);
        this.structureLoc = info.getStructureLocation();

        assignStateVariables();
        assignLuxuries();
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

    /** Biome at the village origin **/
    @Nullable
    public ResourceLocation getBiome() {
        if (level == null || origin == null) return null;
        return level.getBiome(origin).unwrapKey().map(ResourceKey::location).orElse(null);
    }

    /**
     * All resources this village actively produces and trades at its current level:
     * staples always; basics at basicResourceStartLevel and up; assigned luxuries
     * at luxuryResourceStartLevel and up.
     */
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
    private void assignStateVariables()
    {
        ResourceLocation biome = getBiome();

        //Personality: random draw among biome-eligible personalities, permanent
        List<VillagePersonality> eligible = MOD_CONFIG.getPersonalitiesForBiome(biome);
        if (eligible.isEmpty()) {
            this.personalityModifier = NEUTRAL;
        } else {
            this.personalityModifier = eligible.get(RANDOM.nextInt(eligible.size()));
        }
        this.personalityModifierId = personalityModifier.getId();

        //Biome modifier: TODO derive a real modifier from the biome (crop biomes boost bread, etc.)
        this.biomeModifier = NEUTRAL;
        this.biomeModifierId = (biome != null) ? biome.toString() : NEUTRAL.getId();
    }

    /** Weighted draw of assignedLuxuryResourceCount luxuries from the biome-eligible pool, permanent **/
    private void assignLuxuries()
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

    /**
     * Spawns the Mayor Villager entity for this village and constructs the Mayor,
     * passing along the village level and both modifiers.
     */
    public Mayor createMayor()
    {
        if (level == null || origin == null) return null;

        //TODO: custom mayor profession / skin; guard against duplicate mayors
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager != null) {
            villager.moveTo(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0, 0);
            villager.setCustomName(Component.literal("Mayor"));
            villager.setPersistenceRequired();
            level.addFreshEntity(villager);
            this.mayorId = villager.getUUID();
        }

        this.mayor = new Mayor(this, mayorId, villageLevel, personalityModifier, biomeModifier);
        if (pendingMayorData != null) {
            mayor.deserializeNBT(pendingMayorData);
            pendingMayorData = null;
        }
        LoggerProject.logInfo(CLASS_ID + "001", "Mayor created for village " + id
            + " with personality '" + personalityModifierId + "' and biome modifier '" + biomeModifierId + "'");
        return mayor;
    }

    /** The Mayor holding this village's dynamic economy data; constructed lazily **/
    @Nullable
    public Mayor getMayor() {
        if (mayor == null && level != null) {
            this.mayor = new Mayor(this, mayorId, villageLevel, personalityModifier, biomeModifier);
            if (pendingMayorData != null) {
                mayor.deserializeNBT(pendingMayorData);
                pendingMayorData = null;
            }
        }
        return mayor;
    }


    //** PROCESSES - propagated down from VillageManager

    public void tickProcess() {
        Mayor m = getMayor();
        if (m != null) m.tickProcess();
    }

    public void dailyProcess() {
        Mayor m = getMayor();
        if (m != null) m.dailyProcess();
    }

    public void cycleProcess() {
        Mayor m = getMayor();
        if (m != null) m.cycleProcess();
    }


    /** Static Methods **/

    public static VillageEconomy getInstance(LevelAccessor levelAcc, String id) {
        VillageManager manager = VillageManager.getInstance();
        if (manager == null) return null;
        return manager.getVillages().get(ChunkUtil.getChunkPos(id));
    }


    /** IMangedChunkData Overrides **/

    @Override
    public VillageEconomy resolveSubData(LevelAccessor level, String id, @Nullable IMangedChunkData data)
    {
        if (id == null || level == null) return null;
        VillageEconomy subData = (VillageEconomy) data;
        if (subData != null && subData.structureLoc != null) {
            //good, always prioritize serialized data
        } else {
            subData = VillageEconomy.getInstance(level, id);
        }
        if (subData != null)
            VillageManager.addVillage((ServerLevel) level, subData);

        return subData;
    }

    @Override
    public boolean isInit(String subClass) {
        return subClass.equals(VillageEconomy.class.getName()) && this.id != null && !this.id.equals(DEFAULT_ID);
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

        //Mayor dynamic data  //TODO: consider persisting on the Villager entity instead
        if (mayor != null) tag.put("mayorData", mayor.serializeNBT());

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

        //Mayor data applied lazily when the mayor is constructed
        if (tag.contains("mayorData"))
            this.pendingMayorData = tag.getCompound("mayorData");
    }
}
