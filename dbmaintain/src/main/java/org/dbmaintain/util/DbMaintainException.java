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
package org.dbmaintain.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbmaintain.DefaultDbMaintainer;


/**
 * @author Filip Neven
 * @author Tim Ducheyne
 */
public class DbMaintainException extends RuntimeException {

	 /* The logger instance for this class */
    private static Log logger = LogFactory.getLog(DefaultDbMaintainer.class);
    /**
     * Constructor for DbMaintainException.
     */
    public DbMaintainException() {
        super();
    }

    /**
     * Constructor for DbMaintainException.
     *
     * @param message The exception message
     * @param cause   The wrapped exception
     */
    public DbMaintainException(String message, Throwable cause) {
        super(message, cause);
    	logger.error(message, cause);
    }

    /**
     * Constructor for DbMaintainException.
     *
     * @param message The exception message
     */
    public DbMaintainException(String message) {
        super(message);
        logger.error(message);
    }

    /**
     * Constructor for DbMaintainException.
     *
     * @param cause The wrapped exception
     */
    public DbMaintainException(Throwable cause) {
        super(cause);
    }


}
