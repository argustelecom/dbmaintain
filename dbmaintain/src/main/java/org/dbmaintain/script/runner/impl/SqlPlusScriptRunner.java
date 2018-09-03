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
package org.dbmaintain.script.runner.impl;

import static java.lang.System.currentTimeMillis;
import static org.apache.commons.lang3.StringUtils.deleteWhitespace;
import static org.dbmaintain.config.DbMaintainProperties.PROPERTY_SCRIPT_ENCODING;
import static org.dbmaintain.config.DbMaintainProperties.PROPERTY_SQL_PLUS_POST_SCRIPT_FILE_PATH;
import static org.dbmaintain.config.DbMaintainProperties.PROPERTY_SQL_PLUS_PRE_SCRIPT_FILE_PATH;
import static org.dbmaintain.util.FileUtils.createFile;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbmaintain.config.PropertyUtils;
import org.dbmaintain.database.Database;
import org.dbmaintain.database.DatabaseInfo;
import org.dbmaintain.database.Databases;
import org.dbmaintain.script.Script;
import org.dbmaintain.script.parser.ScriptParser;
import org.dbmaintain.script.parser.impl.StatementBuilder;
import org.dbmaintain.util.DbMaintainException;

/**
 * Implementation of a script runner that uses Oracle's SQL plus.
 *
 * @author Tim Ducheyne
 * @author Filip Neven
 */
public class SqlPlusScriptRunner extends BaseNativeScriptRunner {

    /* The logger instance for this class */
    private static Log logger = LogFactory.getLog(SqlPlusScriptRunner.class);

    protected Application application;
    protected String sqlPlusCommand;
    private Properties configuration;
    private Properties scriptParameters;

    public SqlPlusScriptRunner(final Databases databases, final Properties configuration, final Properties scriptParameters, final String sqlPlusCommand) {
        super(databases);
        this.configuration = configuration;
        this.scriptParameters = scriptParameters;
        this.sqlPlusCommand = sqlPlusCommand;
        
        application = createApplication(sqlPlusCommand);
    }

    @Override
    protected void executeScript(final File scriptFile, final Database targetDatabase) throws Exception {
        final File wrapperScriptFile = generateWrapperScriptFile(targetDatabase.getDatabaseInfo(), scriptFile);
        final String[] arguments = {"/nolog", "@" + wrapperScriptFile.getPath()};
        final Application.ProcessOutput processOutput = application.execute(true,configuration,arguments);
        final int exitValue = processOutput.getExitValue();
        boolean error = matchSQLPlusError(processOutput.getOutput());
        // always write sqlplus output to standard out
        logger.info("SQL*Plus exited with code:" + exitValue + " has error: " + error + " Output: " + processOutput.getOutput());
        if (error ||exitValue != 0) {
            logger.info("Failed to execute command. SQL*Plus returned an error.\n");
            throw new DbMaintainException("Failed to execute command. SQL*Plus returned an error.\n" + processOutput.getOutput());
        }
    }

    public boolean matchSQLPlusError(String log) {
		Pattern pattern = Pattern.compile(
				"([0-9]+/[0-9]+\\s+PLS-[0-9]+:)|(^SP2-[0-9]+:)",
				Pattern.MULTILINE);

		Matcher match = pattern.matcher(log);
		return match.find();
	}
    
    protected File generateWrapperScriptFile(final DatabaseInfo databaseInfo, final File targetScriptFile) throws IOException {
        final File temporaryScriptsDir = createTemporaryScriptsDir();
        final File temporaryScriptWrapperFile = new File(temporaryScriptsDir, "wrapper-" + currentTimeMillis() + targetScriptFile.getName());
        temporaryScriptWrapperFile.deleteOnExit();

        final String scriptEncoding = PropertyUtils.getString(PROPERTY_SCRIPT_ENCODING, getConfiguration());
        final String lineSeparator = System.getProperty("line.separator");
        final StringBuilder content = new StringBuilder();

        content.append("set echo off").append(lineSeparator);
        content.append("whenever sqlerror exit sql.sqlcode rollback").append(lineSeparator);
        content.append("whenever oserror exit sql.sqlcode rollback").append(lineSeparator);
        content.append("connect ").append(databaseInfo.getUserName()).append('/').append(databaseInfo.getPassword()).append('@').append(getDatabaseConfigFromJdbcUrl(databaseInfo.getUrl())).append(lineSeparator);
        content.append("alter session set current_schema=").append(databaseInfo.getDefaultSchemaName()).append(";").append(lineSeparator);
        content.append("alter session set ddl_lock_timeout=30;").append(lineSeparator);
        content.append("set echo on").append(lineSeparator);
        
        // определяем все известные параметры для возможности их использования в SQLPlus скрипте
        if (scriptParameters != null){
        	for (Entry<Object, Object> entry : scriptParameters.entrySet()) {
        		// в SQLPlus передаем только параметры без точек и прочих спецсимоволов 
                if (Pattern.matches("(\\w+)",entry.getKey().toString())) {
                	content.append("DEFINE ").append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"").append(lineSeparator);
                }
        	}
        }
        
        // if property set use custom wrapper script
        if (PropertyUtils.containsProperty(PROPERTY_SQL_PLUS_PRE_SCRIPT_FILE_PATH, getConfiguration())) {
            // connect to DB
            // read content from custom script file
            final String preScriptFilePath = PropertyUtils.getString(PROPERTY_SQL_PLUS_PRE_SCRIPT_FILE_PATH, getConfiguration());
            @SuppressWarnings("unchecked")
            final List<String> lines = FileUtils.readLines(new File(preScriptFilePath), scriptEncoding);
            for (final String line : lines) {
                content.append(line).append(lineSeparator);
            }
        }
        
        content.append("@@").append(targetScriptFile.getName()).append(lineSeparator);
        
        if (PropertyUtils.containsProperty(PROPERTY_SQL_PLUS_POST_SCRIPT_FILE_PATH, getConfiguration())) {
            // read content from custom script file
            final String postScriptFilePath = PropertyUtils.getString(PROPERTY_SQL_PLUS_POST_SCRIPT_FILE_PATH, getConfiguration());
            @SuppressWarnings("unchecked")
            final List<String> lines = FileUtils.readLines(new File(postScriptFilePath), scriptEncoding);
            for (final String line : lines) {
                content.append(line).append(lineSeparator);
            }
            content.append(lineSeparator);
        }
        
        content.append("exit sql.sqlcode").append(lineSeparator);
        
        createFile(temporaryScriptWrapperFile, content.toString(), scriptEncoding);
        return temporaryScriptWrapperFile;
    }

    /**
     * Oracle does not support blanks in file names, so remove them from the temp file name.
     *
     * @param script The script that is going to be executed, not null
     * @return The file name without spaces, not null
     */
    @Override
    protected String getTemporaryScriptName(final Script script) {
        final String temporaryScriptName = super.getTemporaryScriptName(script);
        return deleteWhitespace(temporaryScriptName);
    }

    protected Application createApplication(final String sqlPlusCommand) {
        return new Application("SQL*Plus", sqlPlusCommand);
    }

    protected String getDatabaseConfigFromJdbcUrl(final String url) {
        String result="";
        if (url.contains("ldap://")) {
            for (String str : url.substring(url.lastIndexOf("/") + 1).split(",")) {
                if (str.contains("=")) {
                    if (str.startsWith("dc")) {
                        result = result + "." + str.substring(3);
                    }
                } else {
                    result += str;
                }
            }
        }else{
            result = url.substring(url.indexOf("@")+1);
        }
        return result;
    }

    private Properties getConfiguration() {
        return configuration;
    }
    
    private void init() {
		// TODO Auto-generated method stub

	}
}
