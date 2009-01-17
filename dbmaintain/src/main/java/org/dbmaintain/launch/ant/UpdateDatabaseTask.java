/*
 * Copyright 2006-2008,  Unitils.org
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
 *
 * $Id$
 */
package org.dbmaintain.launch.ant;

import org.dbmaintain.config.DbMaintainProperties;
import org.dbmaintain.launch.DbMaintain;

import java.util.Properties;

/**
 * Task that updates the database to the latest version.
 * 
 * @author Filip Neven
 * @author Tim Ducheyne
 */
public class UpdateDatabaseTask extends BaseDatabaseTask {

    private String scriptLocations;
    private Boolean fromScratchEnabled;
    private Boolean autoCreateDbMaintainScriptsTable;
    private Boolean allowOutOfSequenceExecutionOfPatches;
    private Boolean cleanDb;
    private Boolean disableConstraints;
    private Boolean updateSequences;
    private Boolean useLastModificationDates;
    private String scriptExtensions;

    protected void performTask(DbMaintain dbMaintain) {
        dbMaintain.updateDatabase();
    }

    
    @Override
    protected void addTaskConfiguration(Properties configuration) {
        if (scriptLocations != null) {
            configuration.put(DbMaintainProperties.PROPERTY_SCRIPT_LOCATIONS, scriptLocations);
        }
        if (fromScratchEnabled != null) {
            configuration.put(DbMaintainProperties.PROPERTY_FROM_SCRATCH_ENABLED, String.valueOf(fromScratchEnabled));
        }
        if (autoCreateDbMaintainScriptsTable != null) {
            configuration.put(DbMaintainProperties.PROPERTY_AUTO_CREATE_DBMAINTAIN_SCRIPTS_TABLE, String.valueOf(autoCreateDbMaintainScriptsTable));
        }
        if (allowOutOfSequenceExecutionOfPatches != null) {
            configuration.put(DbMaintainProperties.PROPERTY_PATCH_ALLOWOUTOFSEQUENCEEXECUTION, String.valueOf(allowOutOfSequenceExecutionOfPatches));
        }
        if (cleanDb != null) {
            configuration.put(DbMaintainProperties.PROPERTY_CLEANDB_ENABLED, String.valueOf(cleanDb));
        }
        if (disableConstraints != null) {
            configuration.put(DbMaintainProperties.PROPERTY_DISABLE_CONSTRAINTS_ENABLED, disableConstraints);
        }
        if (updateSequences != null) {
            configuration.put(DbMaintainProperties.PROPERTY_UPDATE_SEQUENCES_ENABLED, updateSequences);
        }
        if (scriptExtensions != null) {
            configuration.put(DbMaintainProperties.PROPERTY_SCRIPT_EXTENSIONS, scriptExtensions);
        }
        if (useLastModificationDates != null) {
            configuration.put(DbMaintainProperties.PROPERTY_USESCRIPTFILELASTMODIFICATIONDATES, String.valueOf(useLastModificationDates));
        }
    }

    /**
     * Defines where the scripts can be found that must be executed on the database. Multiple locations may be
     * configured, separated by comma's. A script location can be a folder or a jar file. This property is required.
     * 
     * @param scriptLocations Comma separated list of script locations
     */
    public void setScriptLocations(String scriptLocations) {
        this.scriptLocations = scriptLocations;
    }
    
    /**
     * Sets the fromScratchEnabled property, that indicates the database can be recreated from scratch if needed. 
     * From-scratch recreation is needed in following cases:
     * <ul>
     * <li>A script that was already executed has been modified</li>
     * <li>A new script has been added with an index number lower than the one of an already executed script</li>
     * <li>An script that was already executed has been removed or renamed</li>
     * </ul>
     * If set to false, the dbmaintainer will give an error if one of these situations occurs. The default is false.
     *
     * @param fromScratchEnabled True if the database can be updated from scratch.
     */
    public void setFromScratchEnabled(boolean fromScratchEnabled) {
        this.fromScratchEnabled = fromScratchEnabled;
    }
    
    /**
     * Sets the autoCreateDbMaintainScriptsTable property. If set to true, the table DBMAINTAIN_SCRIPTS will be created 
     * automatically if it does not exist yet. If false, an exception is thrown, indicating how to create the table manually.
     * False by default. 
     *
     * @param autoCreateDbMaintainScriptsTable True if the DBMAINTAIN_SCRIPTS table can be created automatically
     */
    public void setAutoCreateExecutedScriptsTable(boolean autoCreateDbMaintainScriptsTable) {
        this.autoCreateDbMaintainScriptsTable = autoCreateDbMaintainScriptsTable;
    }

    
    /**
     * If this property is set to true, a patch script is allowed to be executed even if another script 
     * with a higher index was already executed.
     * @param allowOutOfSequenceExecutionOfPatches true if out-of-sequence execution of patches is enabled
     */
    public void setAllowOutOfSequenceExecutionOfPatches(boolean allowOutOfSequenceExecutionOfPatches) {
        this.allowOutOfSequenceExecutionOfPatches = allowOutOfSequenceExecutionOfPatches;
    }


    /**
     * Indicates whether the database should be 'cleaned' before scripts are executed. If true, the
     * records of all database tables, except for the ones listed in 'dbMaintainer.preserve.*' or 
     * 'dbMaintain.preserveDataOnly.*' are deleted before executing the first script. False by default.
     * 
     * @param cleanDb True if the database must be 'cleaned' before executing scripts.
     */
    public void setCleanDb(boolean cleanDb) {
        this.cleanDb = cleanDb;
    }

    /**
     * If set to true, all foreign key and not null constraints of the database are automatically disabled after the execution
     * of the scripts. False by default.
     * 
     * @param disableConstraints True if constraints must be disabled.
     */
    public void setDisableConstraints(boolean disableConstraints) {
        this.disableConstraints = disableConstraints;
    }
    
    /**
     * If set to true, all sequences and identity columns are set to a sufficiently high value, so that test data can be 
     * inserted without having manually chosen test record IDs clashing with automatically generated keys.
     * 
     * @param updateSequences True if sequences and identity columns have to be updated.
     */
    public void setUpdateSequences(boolean updateSequences) {
        this.updateSequences = updateSequences;
    }
    
    /**
     * Sets the extensions property, that defines the extensions of the files that are regarded to be database scripts.
     * The extensions should not start with a dot. The default is 'sql,ddl'.
     * 
     * @param scriptExtensions Comma separated list of file extensions.
     */
    public void setScriptExtensions(String scriptExtensions) {
        this.scriptExtensions = scriptExtensions;
    }
    
    /**
     * Defines whether the last modification dates of the scripts files can be used to determine whether the contents of a
     * script has changed. If set to true, DbMaintain will not look at the contents of scripts that were already
     * executed on the database, if the last modification date is still the same. If it did change, it will first calculate 
     * the checksum of the file to verify that the content really changed. Setting this property to true improves performance: 
     * if set to false the checksum of every script must be calculated for each run. True by default.
     *  
     * @param useLastModificationDates True if script file last modification dates can be used.
     */
    public void setUseLastModificationDates(boolean useLastModificationDates) {
        this.useLastModificationDates = useLastModificationDates;
    }

}
