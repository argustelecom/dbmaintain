@echo off
pushd .

call mvn clean install -DskipTests

set urlSnapshot=http://git:8081/artifactory/libs-snapshot-local 
set urlRelease=http://git:8081/artifactory/libs-release-local
set groupID=org.dbmaintain
set repoID=artifactory

set versionArtifacts=%1
if not defined versionArtifacts (
    echo usage: ^<version^>-SNAPSHOT or ^<version^> 
    echo exemple: "deploy-artifacts 2.7.1-SNAPSHOT" or "deploy-artifacts 2.7.1"
    goto done
) 

set SNAPSHOT=%versionArtifacts:-SNAPSHOT=%
if not "%versionArtifacts%" == "%SNAPSHOT%" ( 
    set MAJOR_VER=%SNAPSHOT%
    echo use classifier -SNAPSHOT
    goto use-snapshot
) 

set MAJOR_VER=%SNAPSHOT%
echo not use classifier -SNAPSHOT
goto not-use-snapshot


:use-snapshot

    call mvn source:jar
    call mvn deploy:deploy-file -Durl=%urlSnapshot% -DrepositoryId=%repoID% -Dfile=target/dbmaintain-%MAJOR_VER%.jar -DgroupId=%groupID% -DartifactId=dbmaintain -Dversion=%versionArtifacts% -Dpackaging=jar -DpomFile=pom.xml -Dsources=target/dbmaintain-%MAJOR_VER%-sources.jar
    if errorlevel 1 goto deploy_error
    
    call mvn deploy:deploy-file -Durl=%urlSnapshot% -DrepositoryId=%repoID% -Dfile=target/dbmaintain-cmd-%MAJOR_VER%.zip -DgroupId=%groupID% -DartifactId=dbmaintain-cmd -Dversion=%versionArtifacts% -Dpackaging=zip
    if errorlevel 1 goto deploy_error

    goto done
                                                                        
:not-use-snapshot
    call mvn source:jar
    call mvn deploy:deploy-file -Durl=%urlRelease% -DrepositoryId=%repoID% -Dfile=target/dbmaintain-%MAJOR_VER%.jar -DgroupId=%groupID% -DartifactId=dbmaintain -Dversion=%versionArtifacts% -Dpackaging=jar -DpomFile=pom.xml -Dsources=target/dbmaintain-%MAJOR_VER%-sources.jar
    if errorlevel 1 goto deploy_error
    
    call mvn deploy:deploy-file -Durl=%urlRelease% -DrepositoryId=%repoID% -Dfile=target/dbmaintain-cmd-%MAJOR_VER%.zip -DgroupId=%groupID% -DartifactId=dbmaintain-cmd -Dversion=%versionArtifacts% -Dpackaging=zip
    if errorlevel 1 goto deploy_error
    
    goto done

:deploy_error
    echo *********************************************************
    echo * deploy or build error occured, artifacts not deployed *
    echo * error level is %ERRORLEVEL%                           *
    echo *********************************************************
    goto exit

:done
    echo build success

:exit