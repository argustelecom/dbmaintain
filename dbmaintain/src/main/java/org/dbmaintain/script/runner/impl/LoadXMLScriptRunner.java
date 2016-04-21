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

import java.io.File;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;

import javax.sql.DataSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbmaintain.database.Database;
import org.dbmaintain.database.Databases;
import org.dbmaintain.database.SQLHandler;
import org.dbmaintain.script.Script;
import org.dbmaintain.util.DbMaintainException;

/**
 * Implementation of a script runner that load XML to Oracle.
 *
 * @author ARGUS
 */
public class LoadXMLScriptRunner extends BaseNativeScriptRunner {

    /* The logger instance for this class */
    private static Log logger = LogFactory.getLog(LoadXMLScriptRunner.class);

    private String loadXMLFunction; 
    private String encoding;
    private SQLHandler sqlHandler;
    private String scriptPath;

    public LoadXMLScriptRunner(final Databases databases, final SQLHandler sqlHandler, final String loadXMLFunction) {
        super(databases);
        this.loadXMLFunction = loadXMLFunction;
        this.sqlHandler = sqlHandler;
    }
    @Override
    public void execute(Script script) {
    	scriptPath = script.getFileName();
    	encoding = script.getScriptContentHandle().getEncoding();
    	super.execute(script);
    }
	@Override
	protected void executeScript(File scriptFile, Database targetDatabase)
			throws Exception {
    	logger.debug("loading XML file with " + loadXMLFunction);
    	
    	String xml = FileUtils.readFileToString(scriptFile, encoding);
    	DataSource dataSource = targetDatabase.getDataSource();
    	Connection connection = sqlHandler.getConnection(dataSource);

    	Clob clobxml = connection.createClob();
		String sql = "CALL " + loadXMLFunction + "( ? , ? )";
		PreparedStatement ps = connection.prepareStatement(sql);
    	try {
    		clobxml.setString(1, xml);
    		ps.setString(1, scriptPath);
    		ps.setClob(2, clobxml);
    		ps.execute();
    		connection.commit();
        } catch (DbMaintainException e) {
        	connection.rollback();
        	throw e;
        }
    	finally {
        	clobxml.free();
    		ps.close();
        }    			
	}

}
