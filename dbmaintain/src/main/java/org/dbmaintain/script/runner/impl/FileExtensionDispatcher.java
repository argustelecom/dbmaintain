/*
 * Copyright DbMaintain.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * under the License.
 */

package org.dbmaintain.script.runner.impl;

import static org.dbmaintain.config.DbMaintainProperties.PROPERTY_CHMOD_COMMAND;
import static org.dbmaintain.config.DbMaintainProperties.PROPERTY_LOAD_XML_FUNCTION;
import static org.dbmaintain.config.DbMaintainProperties.PROPERTY_SQL_LOADER_COMMAND;
import static org.dbmaintain.config.DbMaintainProperties.PROPERTY_SQL_PLUS_COMMAND;

import java.util.Map;
import java.util.Properties;
import org.dbmaintain.config.PropertyUtils;
import org.dbmaintain.database.Databases;
import org.dbmaintain.database.SQLHandler;
import org.dbmaintain.script.Script;
import org.dbmaintain.script.parser.ScriptParserFactory;
import org.dbmaintain.script.runner.ScriptRunner;
import org.dbmaintain.script.runner.SqlPlusScriptRunnerFactory;

/**
 * Implementation of a script runner which calls other script runner depending on the file name suffix
 * 
 * @author Christian Liebhardt
 * @author ARGUS
 */
public class FileExtensionDispatcher implements ScriptRunner {
    
    protected Databases databases;
    protected SQLHandler sqlHandler;
    protected Properties configuration;
    protected Properties scriptParameters;
    protected String sqlLoaderCommand;
    protected String sqlPlusCommand;
    protected String chmodCommand;
    protected String loadXMLFunction;
    protected Map<String, ScriptParserFactory> databaseDialectScriptParserFactoryMap;
    
    public FileExtensionDispatcher(Databases databases, 
            SQLHandler sqlHandler,
            final Properties configuration,
            final Properties scriptParameters,
            Map<String, ScriptParserFactory> databaseDialectScriptParserFactoryMap) {
        this.databases = databases;
        this.sqlHandler = sqlHandler;
        this.configuration = configuration;
        this.scriptParameters = scriptParameters;
        this.sqlLoaderCommand = PropertyUtils.getString(PROPERTY_SQL_LOADER_COMMAND, configuration);
        this.sqlPlusCommand = PropertyUtils.getString(PROPERTY_SQL_PLUS_COMMAND, configuration);
        this.chmodCommand = PropertyUtils.getString(PROPERTY_CHMOD_COMMAND, configuration);
        this.loadXMLFunction = PropertyUtils.getString(PROPERTY_LOAD_XML_FUNCTION, configuration);
        this.databaseDialectScriptParserFactoryMap = databaseDialectScriptParserFactoryMap;
    }

    public void execute(Script script) {
    	ScriptRunner runner;
        if (script.getFileName().matches("^.*\\.(ldr|ctl)$")) {
            runner = new SqlLoaderScriptRunner(databases, sqlLoaderCommand);
        }
        else if (script.getFileName().matches("^.*\\.(sh)$")) {
            runner = new ShellScriptRunner(databases, chmodCommand);
        }
        else if (script.getFileName().matches("^.*\\.(sqlplus)$")) {
        	runner = new SqlPlusScriptRunner(databases, configuration, scriptParameters , sqlPlusCommand);
        }
        else if (script.getFileName().matches("^.*\\.(xml)$")) {
        	runner = new LoadXMLScriptRunner(databases, sqlHandler, loadXMLFunction);
        }
        else {
            runner = new JdbcScriptRunner(databaseDialectScriptParserFactoryMap, databases, sqlHandler); 
        }
        runner.execute(script);
    }

    public void initialize() {
    }

    public void close() {
    }

}