/*
 * Copyright DbMaintain.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dbmaintain.script.analyzer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbmaintain.DefaultDbMaintainer;
import org.dbmaintain.script.ExecutedScript;
import org.dbmaintain.script.Script;
import org.dbmaintain.script.executedscriptinfo.ExecutedScriptInfoSource;
import org.dbmaintain.script.repository.ScriptRepository;

import java.util.*;

import static org.dbmaintain.script.analyzer.ScriptUpdateType.*;

/**
 * An instance of this class can compare the info that we have about previously executed scripts with the new scripts.
 * It decides which of these scripts were added, updated, deleted or renamed. It also decides for each script update
 * whether it is a regular or irregular one.
 *
 * @author Filip Neven
 * @author Tim Ducheyne
 * @since 29-dec-2008
 */
public class ScriptUpdatesAnalyzer {

    private static final String ARGUS_MAGIC_TEXT = "-- I have read and understand the implications of changing the incremental script";
    private static final int ARGUS_MAGIC_TEXT_LENGTH = ARGUS_MAGIC_TEXT.length();


    /* The logger instance for this class */
    private static Log logger = LogFactory.getLog(DefaultDbMaintainer.class);
    /* Initialization data */
    private final ScriptRepository scriptRepository;
    private final ExecutedScriptInfoSource executedScriptInfoSource;
    private final boolean useScriptFileLastModificationDates;
    private final boolean allowOutOfSequenceExecutionOfPatchScripts;
    /* Sets that contain the result of the analysis: each set contains a specific type of script updates */
    private final SortedSet<ScriptUpdate> regularlyAddedOrModifiedScripts = new TreeSet<>();
    private final SortedSet<ScriptUpdate> irregularlyUpdatedScripts = new TreeSet<>();
    private final SortedSet<ScriptUpdate> regularlyDeletedRepeatableScripts = new TreeSet<>();
    private final SortedSet<ScriptUpdate> regularlyAddedPatchScripts = new TreeSet<>();
    private final SortedSet<ScriptUpdate> regularlyUpdatedPreprocessingScripts = new TreeSet<>();
    private final SortedSet<ScriptUpdate> regularlyUpdatedPostprocessingScripts = new TreeSet<>();
    private final SortedSet<ScriptUpdate> regularlyRenamedScripts = new TreeSet<>();
    private final SortedSet<ScriptUpdate> ignoredScripts = new TreeSet<>();
    /* Sets that contain working data that is assembled during the analysis */
    private final Map<ExecutedScript, Script> renamedIndexedScripts = new HashMap<>();
    private final Map<Script, ExecutedScript> scriptExecutedScriptMap = new HashMap<>();
    /* Lazily initialized data, that is cached during analysis to avoid repeated calculation of the contents */
    private Map<String, Script> scriptNameScriptMap;
    private Map<String, Set<Script>> checkSumScriptMap;
    // successor skripts in the database
    private boolean ignoreDeletions; // Ignore if the db state is newer, i.e. there are allready

    /**
     * Creates a new instance that will compare the info from the given {@link ExecutedScriptInfoSource} with the current
     * scripts from the given {@link org.dbmaintain.script.repository.ScriptRepository}. It also needs to know whether a new patch script with a lower
     * index is a regular or irregular script update
     *
     * @param scriptRepository                          exposes the current set of scripts
     * @param executedScriptInfoSource                  provides info on the script that were executed on the database
     * @param useScriptFileLastModificationDates        whether the last modification date of the scripts can be used to determine if a script has changed.
     * @param allowOutOfSequenceExecutionOfPatchScripts whether scripts marked as patch scripts may be executed out-of-sequence
     */
    public ScriptUpdatesAnalyzer(ScriptRepository scriptRepository, ExecutedScriptInfoSource executedScriptInfoSource,
                                 boolean useScriptFileLastModificationDates, boolean allowOutOfSequenceExecutionOfPatchScripts, boolean ignoreDeletions) {
        this.scriptRepository = scriptRepository;
        this.executedScriptInfoSource = executedScriptInfoSource;
        this.useScriptFileLastModificationDates = useScriptFileLastModificationDates;
        this.allowOutOfSequenceExecutionOfPatchScripts = allowOutOfSequenceExecutionOfPatchScripts;
        this.ignoreDeletions = ignoreDeletions;
    }

    /**
     * Compares the info that we have about previously executed scripts with the new scripts.
     * It decides which of these scripts were added, updated, deleted or renamed. It also decides for each script update
     * whether it is a regular or irregular one.
     *
     * @return {@link org.dbmaintain.script.analyzer.ScriptUpdates} object that represents all updates that were performed to the scripts since the last
     * database update
     */
    public ScriptUpdates calculateScriptUpdates() {
        // Iterate over the already executed scripts to find out whether the contents of some scripts has been modified
        // since the last update. We also map the executed scripts with their script counterparts, to be able to verify
        // afterwards if scripts have been renamed or deleted.
        for (ExecutedScript executedScript : executedScriptInfoSource.getExecutedScripts()) {
            Script scriptWithSameName = findScriptWithSameName(executedScript);
            if (scriptWithSameName != null) {
                // The script with this name still exists. We keep the mapping in the scriptExecutedScriptMap
                scriptExecutedScriptMap.put(scriptWithSameName, executedScript);
                // Check if the content didn't change


                final Script script = executedScript.getScript();
                if (!script.isScriptContentEqualTo(scriptWithSameName, useScriptFileLastModificationDates)) {
                	// ARGUS fix. If we have magic text, then just update Incremental script MD5. Cause we fix floating error
                	if (script.isIncremental() && 
                			scriptWithSameName.getScriptContentHandle() != null &&
                			Objects.equals(ARGUS_MAGIC_TEXT, scriptWithSameName.getScriptContentHandle().getScriptContentsAsString(ARGUS_MAGIC_TEXT_LENGTH, false))) {
                        registerScriptRename(executedScript, scriptWithSameName);
                	} else {                	
                        registerScriptUpdate(scriptWithSameName);
                	}
                } else if (!executedScript.isSuccessful() && script.isPostProcessingScript()) {
                    registerPostprocessingScriptUpdate(POSTPROCESSING_SCRIPT_FAILURE_RERUN, scriptWithSameName);
                } else if (!executedScript.isSuccessful() && script.isPreProcessingScript()) {
                    registerPreprocessingScriptUpdate(PREPROCESSING_SCRIPT_FAILURE_RERUN, scriptWithSameName);
                }
            }
        }

        // Look for renamed or deleted scripts
        if (scriptExecutedScriptMap.size() < executedScriptInfoSource.getExecutedScripts().size()) {
            // There are executed scripts for which a script with the same name cannot be found anymore. We are going to find
            // out what has happened to the executed scripts that could not be mapped directly: were they renamed or were they deleted?
            for (ExecutedScript executedScript : executedScriptInfoSource.getExecutedScripts()) {
                Script scriptWithSameName = findScriptWithSameName(executedScript);
                if (scriptWithSameName == null) {
                    // Find out if there's a script with the same content, which is not yet mapped with an executed script,
                    // in this case we conclude that the script has been renamed.
                    Script newScriptWithSameContent = findNewScriptWithSameContent(executedScript);
                    if (newScriptWithSameContent != null) {
                        registerScriptRename(executedScript, newScriptWithSameContent);
                    } else {
                        if (ignoreDeletions) {
                            registerIgnoredScript(executedScript.getScript());
                        } else {
                            registerScriptDeletion(executedScript.getScript());
                        }
                    }
                }
            }

            // If the sequence of the indexed scripts changed, all indexed script renames are registered as irregular
            // script renames. If it didn't change, all indexed script renames are registered as regular script renames
            //Killian: strange logic. versions compared not by  ScriptIndexes but by strange function
            //if (sequenceOfIndexedScriptsChangedDueToRenames()) {
            //    registerIrregularIncrementalScriptRenames();
            //} else {
            //    registerRegularIncrementalScriptRenames();
            //}
        }

        // Look for newly added scripts. A script is new if it's not mapped to an executed script in the scriptExecuteScriptMap,
        // which also contains the scripts that were renamed
        for (Script script : scriptRepository.getAllScripts()) {
            if (!scriptExecutedScriptMap.containsKey(script)) {
                registerScriptAddition(script);
            }
        }

        return new ScriptUpdates(regularlyAddedOrModifiedScripts, irregularlyUpdatedScripts, regularlyDeletedRepeatableScripts, regularlyAddedPatchScripts, regularlyUpdatedPreprocessingScripts,
                regularlyUpdatedPostprocessingScripts, regularlyRenamedScripts, ignoredScripts);
    }

    private void registerIgnoredScript(Script script) {
        ignoredScripts.add(new ScriptUpdate(INDEXED_SCRIPT_DELETED, script));

    }

    /**
     * Register the given script as a newly added one
     *
     * @param script The newly added script
     */
    protected void registerScriptAddition(Script script) {
        if (script.isIncremental()) {
            Script scriptWithHighestScriptIndex = getExecutedScriptWithHighestScriptIndex();
            if (scriptWithHighestScriptIndex == null || script.compareTo(scriptWithHighestScriptIndex) > 0) {
                registerRegularScriptUpdate(HIGHER_INDEX_SCRIPT_ADDED, script);
            } else {
                // ARGUS fix. add or statement. cause it's LOWER INDEX is normal )
                if (script.isPatchScript() || allowOutOfSequenceExecutionOfPatchScripts) {
                    if (allowOutOfSequenceExecutionOfPatchScripts) {
                        registerRegularlyAddedPatchScript(LOWER_INDEX_PATCH_SCRIPT_ADDED, script);
                    } else {
                        registerIrregularScriptUpdate(LOWER_INDEX_PATCH_SCRIPT_ADDED, script);
                    }
                } else {
                    registerIrregularScriptUpdate(LOWER_INDEX_NON_PATCH_SCRIPT_ADDED, script);
                }
            }
        } else if (script.isRepeatable()) {
            registerRegularScriptUpdate(REPEATABLE_SCRIPT_ADDED, script);
        } else if (script.isPreProcessingScript()) {
            registerPreprocessingScriptUpdate(PREPROCESSING_SCRIPT_ADDED, script);
        } else if (script.isPostProcessingScript()) {
            registerPostprocessingScriptUpdate(POSTPROCESSING_SCRIPT_ADDED, script);
        }
    }


    /**
     * Register that the given script's content has changed since the last update
     *
     * @param script The script who's content has changed
     */
    protected void registerScriptUpdate(Script script) {
        if (script.isIncremental()) {
            registerIrregularScriptUpdate(INDEXED_SCRIPT_UPDATED, script);
        } else if (script.isRepeatable()) {
            registerRegularScriptUpdate(REPEATABLE_SCRIPT_UPDATED, script);
        } else if (script.isPreProcessingScript()) {
            registerPreprocessingScriptUpdate(PREPROCESSING_SCRIPT_UPDATED, script);
        } else if (script.isPostProcessingScript()) {
            registerPostprocessingScriptUpdate(POSTPROCESSING_SCRIPT_UPDATED, script);
        }
    }


    /**
     * Register that the given script has been deleted since the last update
     *
     * @param deletedScript The script that has been deleted
     */
    protected void registerScriptDeletion(Script deletedScript) {
        if (deletedScript.isIncremental()) {
            registerIrregularScriptUpdate(INDEXED_SCRIPT_DELETED, deletedScript);
        } else if (deletedScript.isRepeatable()) {
            registerRepeatableScriptDeletion(deletedScript);
        } else if (deletedScript.isPreProcessingScript()) {
            registerPreprocessingScriptUpdate(PREPROCESSING_SCRIPT_DELETED, deletedScript);
        } else if (deletedScript.isPostProcessingScript()) {
            registerPostprocessingScriptUpdate(POSTPROCESSING_SCRIPT_DELETED, deletedScript);
        }
    }


    /**
     * Register that the given script has been renamed to the given new script since the last update
     *
     * @param executedScript The script like it was during the last update
     * @param renamedTo      The renamed script
     */
    protected void registerScriptRename(ExecutedScript executedScript, Script renamedTo) {
        final Script script = executedScript.getScript();
        if (script.isIncremental() && renamedTo.isIncremental()) {
            scriptExecutedScriptMap.put(renamedTo, executedScript);
            renamedIndexedScripts.put(executedScript, renamedTo);

            if (script.getScriptIndexes().compareTo(renamedTo.getScriptIndexes()) == 0) {
                registerRegularScriptRename(ScriptUpdateType.INDEXED_SCRIPT_RENAMED, script, renamedTo);
            } else {
                logger.error("Script " + script.getFileName()
                        + " (newname: " + renamedTo.getFileName()
                        + ") version differ: "
                        + script.getScriptIndexes().getIndexesString()
                        + " vs "
                        + renamedTo.getScriptIndexes().getIndexesString());
                registerIrregularScriptUpdate(ScriptUpdateType.INDEXED_SCRIPT_RENAMED_SCRIPT_SEQUENCE_CHANGED, script, renamedTo);
            }
        } else if (script.isRepeatable() && renamedTo.isRepeatable()) {
            scriptExecutedScriptMap.put(renamedTo, executedScript);
            registerRegularScriptRename(REPEATABLE_SCRIPT_RENAMED, script, renamedTo);
        } else if (script.isPreProcessingScript() && renamedTo.isPreProcessingScript()) {
            scriptExecutedScriptMap.put(renamedTo, executedScript);
            registerPreprocessingScriptRename(PREPROCESSING_SCRIPT_RENAMED, script, renamedTo);
        } else if (script.isPostProcessingScript() && renamedTo.isPostProcessingScript()) {
            scriptExecutedScriptMap.put(renamedTo, executedScript);
            registerPostprocessingScriptRename(POSTPROCESSING_SCRIPT_RENAMED, script, renamedTo);
        }
    }


    /**
     * @return whether the sequence of the indexed scripts has changed due to indexed scripts that were renamed since
     * the last update
     */
    protected boolean sequenceOfIndexedScriptsChangedDueToRenames() {
        Iterator<Script> indexedScriptsIterator = scriptRepository.getIndexedScripts().iterator();
        for (ExecutedScript executedScript : executedScriptInfoSource.getExecutedScripts()) {
            Script scriptInNewSequence = renamedIndexedScripts.get(executedScript);
            if (scriptInNewSequence == null) {
                scriptInNewSequence = executedScript.getScript();
            }
            boolean foundScriptInNewSequenceInIndexedScripts = false;
            while (!foundScriptInNewSequenceInIndexedScripts) {
                if (!indexedScriptsIterator.hasNext()) {
                    return true;
                }
                if (scriptInNewSequence.equals(indexedScriptsIterator.next())) {
                    foundScriptInNewSequenceInIndexedScripts = true;
                }
            }
        }
        return false;
    }


    /**
     * Take all script renames that were registered using the method {@link #registerScriptRename(ExecutedScript, Script)}
     * and register them all as regular script renames.
     */
    protected void registerRegularIncrementalScriptRenames() {
        for (ExecutedScript renamedIndexedExecutedScript : renamedIndexedScripts.keySet()) {
            registerRegularScriptRename(ScriptUpdateType.INDEXED_SCRIPT_RENAMED, renamedIndexedExecutedScript.getScript(), renamedIndexedScripts.get(renamedIndexedExecutedScript));
        }
    }

    /**
     * Take all script renames that were registered using the method {@link #registerScriptRename(ExecutedScript, Script)}
     * and register them all as irregular script renames.
     */
    protected void registerIrregularIncrementalScriptRenames() {
        for (ExecutedScript renamedIndexedExecutedScript : renamedIndexedScripts.keySet()) {
            registerIrregularScriptUpdate(ScriptUpdateType.INDEXED_SCRIPT_RENAMED_SCRIPT_SEQUENCE_CHANGED,
                    renamedIndexedExecutedScript.getScript(), renamedIndexedScripts.get(renamedIndexedExecutedScript));
        }
    }

    /**
     * @param executedScript The script as executed during a previous update
     * @return A script that is not yet mapped to an executed script, but that has the same content as the given one
     */
    protected Script findNewScriptWithSameContent(ExecutedScript executedScript) {
        SortedSet<Script> newScriptsWithSameContent = new TreeSet<>();
        Set<Script> scriptsWithSameContent = getCheckSumScriptMap().get(executedScript.getScript().getCheckSum());
        if (scriptsWithSameContent != null) {
            for (Script scriptWithSameContent : scriptsWithSameContent) {
                if (!scriptExecutedScriptMap.containsKey(scriptWithSameContent)) {
                    newScriptsWithSameContent.add(scriptWithSameContent);
                }
            }
        }
        if (newScriptsWithSameContent.size() == 1) {
            return newScriptsWithSameContent.first();
        }
        return null;
    }

    /**
     * @param executedScript A script as executed during a previous update
     * @return The script with the same name as the given one
     */
    protected Script findScriptWithSameName(ExecutedScript executedScript) {
        return getScriptNameScriptMap().get(executedScript.getScript().getFileName());
    }

    /**
     * @return The already executed scripts, as a map from scriptName => ExecutedScript
     */
    protected Map<String, ExecutedScript> getScriptNameExecutedScriptMap() {
        Map<String, ExecutedScript> scriptNameAlreadyExecutedScriptMap = new HashMap<>();
        for (ExecutedScript executedScript : executedScriptInfoSource.getExecutedScripts()) {
            scriptNameAlreadyExecutedScriptMap.put(executedScript.getScript().getFileName(), executedScript);
        }
        return scriptNameAlreadyExecutedScriptMap;
    }

    /**
     * @return All scripts, as a map from scriptName => Script
     */
    protected Map<String, Script> getScriptNameScriptMap() {
        if (scriptNameScriptMap == null) {
            scriptNameScriptMap = new HashMap<>();
            for (Script script : scriptRepository.getAllScripts()) {
                scriptNameScriptMap.put(script.getFileName(), script);
            }
        }
        return scriptNameScriptMap;
    }

    /**
     * @return All scripts, as a map from checksum => Script
     */
    protected Map<String, Set<Script>> getCheckSumScriptMap() {
        if (checkSumScriptMap == null) {
            checkSumScriptMap = new HashMap<>();
            for (Script script : scriptRepository.getAllScripts()) {
                Set<Script> scriptsWithCheckSum = checkSumScriptMap.computeIfAbsent(script.getCheckSum(), k -> new HashSet<>());
                scriptsWithCheckSum.add(script);
            }
        }
        return checkSumScriptMap;
    }


    /**
     * @return The executed scripts with the highest script index
     */
    protected Script getExecutedScriptWithHighestScriptIndex() {
        Script result = null;
        for (ExecutedScript executedScript : executedScriptInfoSource.getExecutedScripts()) {
            if (executedScript.getScript().isIncremental() && (result == null || executedScript.getScript().compareTo(result) > 0)) {
                result = executedScript.getScript();
            }
        }
        return result;
    }


    protected void registerRegularScriptUpdate(ScriptUpdateType scriptUpdateType, Script script) {
        regularlyAddedOrModifiedScripts.add(new ScriptUpdate(scriptUpdateType, script));
    }


    protected void registerIrregularScriptUpdate(ScriptUpdateType scriptUpdateType, Script script) {
        irregularlyUpdatedScripts.add(new ScriptUpdate(scriptUpdateType, script));
    }


    protected void registerIrregularScriptUpdate(ScriptUpdateType scriptUpdateType, Script script1, Script script2) {
        irregularlyUpdatedScripts.add(new ScriptUpdate(scriptUpdateType, script1, script2));
    }


    protected void registerRepeatableScriptDeletion(Script script) {
        regularlyDeletedRepeatableScripts.add(new ScriptUpdate(REPEATABLE_SCRIPT_DELETED, script));
    }


    protected void registerRegularlyAddedPatchScript(ScriptUpdateType scriptUpdateType, Script script) {
        regularlyAddedPatchScripts.add(new ScriptUpdate(scriptUpdateType, script));
    }


    protected void registerPreprocessingScriptUpdate(ScriptUpdateType scriptUpdateType, Script script) {
        regularlyUpdatedPreprocessingScripts.add(new ScriptUpdate(scriptUpdateType, script));
    }

    protected void registerPostprocessingScriptUpdate(ScriptUpdateType scriptUpdateType, Script script) {
        regularlyUpdatedPostprocessingScripts.add(new ScriptUpdate(scriptUpdateType, script));
    }


    protected void registerPreprocessingScriptRename(ScriptUpdateType scriptUpdateType, Script originalScript, Script renamedToScript) {
        regularlyUpdatedPreprocessingScripts.add(new ScriptUpdate(scriptUpdateType, originalScript, renamedToScript));
    }

    protected void registerPostprocessingScriptRename(ScriptUpdateType scriptUpdateType, Script originalScript, Script renamedToScript) {
        regularlyUpdatedPostprocessingScripts.add(new ScriptUpdate(scriptUpdateType, originalScript, renamedToScript));
    }


    protected void registerRegularScriptRename(ScriptUpdateType scriptUpdateType, Script originalScript, Script renamedScript) {
        regularlyRenamedScripts.add(new ScriptUpdate(scriptUpdateType, originalScript, renamedScript));
    }

}
