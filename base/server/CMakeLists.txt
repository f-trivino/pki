project(server)

add_subdirectory(healthcheck)

# build server classes
javac(pki-server-classes
    DEPENDS
        pki-certsrv-jar pki-tools-jar pki-tomcat-jar
    SOURCES
        src/main/java/*.java
    CLASSPATH
        ${SLF4J_API_JAR} ${JAXB_API_JAR}
        ${COMMONS_CODEC_JAR} ${COMMONS_IO_JAR} ${COMMONS_LANG3_JAR}
        ${COMMONS_CLI_JAR}
        ${HTTPCORE_JAR} ${HTTPCLIENT_JAR}
        ${JACKSON2_ANNOTATIONS_JAR} ${JACKSON2_JAXB_ANNOTATIONS_JAR}
        ${JACKSON2_CORE_JAR} ${JACKSON2_DATABIND_JAR}
        ${JSS_JAR} ${JSS_SYMKEY_JAR}
        ${LDAPJDK_JAR}
        ${SERVLET_JAR} ${TOMCAT_CATALINA_JAR} ${TOMCAT_UTIL_JAR}
        ${TOMCATJSS_JAR}
        ${JAXRS_API_JAR} ${RESTEASY_JAXRS_JAR}
        ${PKI_TOMCAT_JAR} ${PKI_CERTSRV_JAR}
    OUTPUT_DIR
        ${CMAKE_CURRENT_BINARY_DIR}/classes
)

configure_file(
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/resources/META-INF/MANIFEST.MF
    ${CMAKE_CURRENT_BINARY_DIR}/MANIFEST.MF
)

# build pki-cms.jar
jar(pki-cms-jar
    DEPENDS
        pki-server-classes
    CREATE
        ${CMAKE_BINARY_DIR}/dist/pki-cms.jar
    OPTIONS
        m
    PARAMS
        ${CMAKE_CURRENT_BINARY_DIR}/MANIFEST.MF
    INPUT_DIR
        ${CMAKE_CURRENT_BINARY_DIR}/classes
    INPUT_DIR
        ${CMAKE_CURRENT_SOURCE_DIR}/src/main/resources
    FILES
        audit-events.properties
        LogMessages.properties
        UserMessages.properties
)

set(PKI_CMS_JAR ${CMAKE_BINARY_DIR}/dist/pki-cms.jar CACHE INTERNAL "pki-cms jar file")

if(RUN_TESTS)
    # build pki-server-test
    javac(pki-server-test-classes
        DEPENDS
            pki-certsrv-test-classes pki-certsrv-jar pki-cms-jar
        SOURCES
            src/test/java/*.java
        CLASSPATH
            ${PKI_CERTSRV_JAR} ${PKI_CMS_JAR}
            ${LDAPJDK_JAR} ${SERVLET_JAR}
            ${JSS_JAR} ${JSS_SYMKEY_JAR}
            ${HAMCREST_JAR} ${JUNIT_JAR} ${COMMONS_CODEC_JAR} ${COMMONS_IO_JAR}
            ${JACKSON2_ANNOTATIONS_JAR} ${JACKSON2_CORE_JAR} ${JACKSON2_DATABIND_JAR}
            ${CMAKE_BINARY_DIR}/test/classes
        OUTPUT_DIR
            ${CMAKE_BINARY_DIR}/test/classes
    )

    # create test target
    execute_process(
        COMMAND bash "-c"
        "grep -ilR @Test ${PROJECT_SOURCE_DIR} \
        | cut -d':' -f1 \
        | awk -F '/src/test/java/' '{ print $2 }' \
        | sed 's/.java/;/g' \
        | sed 's!/!.!g' \
        | tr -d '\n'"
        OUTPUT_VARIABLE DISCOVERED_TESTS
    )

    add_junit_test(test-pki-server
        DEPENDS
            pki-server-test-classes
        CLASSPATH
            ${SLF4J_API_JAR} ${SLF4J_SIMPLE_JAR}
            ${PKI_CERTSRV_JAR} ${PKI_CMS_JAR}
            ${LDAPJDK_JAR} ${SERVLET_JAR}
            ${COMMONS_CODEC_JAR} ${COMMONS_LANG3_JAR}
            ${JSS_JAR} ${JSS_SYMKEY_JAR}
            ${JACKSON2_ANNOTATIONS_JAR} ${JACKSON2_CORE_JAR} ${JACKSON2_DATABIND_JAR}
            ${HAMCREST_JAR} ${JUNIT_JAR} ${COMMONS_IO_JAR}
            ${CMAKE_BINARY_DIR}/test/classes
        TESTS
            ${DISCOVERED_TESTS}
        REPORTS_DIR
            reports
    )
endif(RUN_TESTS)

# Create /usr/share/pki/server/lib. This can be customized for different platforms in RPM spec.

add_custom_target(pki-server-lib ALL
    COMMENT "Creating links for PKI server library")

add_custom_command(
    TARGET pki-server-lib
    COMMAND ${CMAKE_COMMAND} -E make_directory lib
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${SLF4J_API_JAR} lib/slf4j-api.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${SLF4J_JDK14_JAR} lib/slf4j-jdk14.jar
)

# Create /usr/share/pki/server/common/lib. This can be customized for different platforms in RPM spec.

add_custom_target(pki-server-common-lib ALL
    COMMENT "Creating links for PKI server common library")

add_custom_command(
    TARGET pki-server-common-lib
    COMMAND ${CMAKE_COMMAND} -E make_directory common/lib
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${COMMONS_CODEC_JAR} common/lib/commons-codec.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${COMMONS_IO_JAR} common/lib/commons-io.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${COMMONS_LANG3_JAR} common/lib/commons-lang3.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${COMMONS_LOGGING_JAR} common/lib/commons-logging.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${COMMONS_NET_JAR} common/lib/commons-net.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${HTTPCLIENT_JAR} common/lib/httpclient.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${HTTPCORE_JAR} common/lib/httpcore.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JACKSON2_CORE_JAR} common/lib/jackson-core.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JACKSON2_DATABIND_JAR} common/lib/jackson-databind.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JACKSON2_ANNOTATIONS_JAR} common/lib/jackson-annotations.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JACKSON2_JAXRS_BASE_JAR} common/lib/jackson-jaxrs-base.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JACKSON2_JAXRS_JSON_PROVIDER_JAR} common/lib/jackson-jaxrs-json-provider.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JACKSON2_JAXB_ANNOTATIONS_JAR} common/lib/jackson-module-jaxb-annotations.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JAVAX_ACTIVATION_JAR} common/lib/javax.activation.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JAVAX_ANNOTATIONS_API_JAR} common/lib/javax.annotations-api.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JAXB_API_JAR} common/lib/jaxb-api.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JAXRS_API_JAR} common/lib/jaxrs-api.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JBOSS_LOGGING_JAR} common/lib/jboss-logging.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JSS_JAR} common/lib/jss.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JSS_SYMKEY_JAR} common/lib/jss-symkey.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${LDAPJDK_JAR} common/lib/ldapjdk.jar
    COMMAND ln -sf /usr/share/java/pki/pki-tomcat.jar ${CMAKE_CURRENT_BINARY_DIR}/common/lib/pki-tomcat.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${RESTEASY_CLIENT_JAR} common/lib/resteasy-client.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${RESTEASY_JACKSON2_PROVIDER_JAR} common/lib/resteasy-jackson2-provider.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${RESTEASY_JAXRS_JAR} common/lib/resteasy-jaxrs.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${TOMCATJSS_JAR} common/lib/tomcatjss.jar
)

# Create /usr/share/pki/server/webapps/pki/WEB-INF/lib. This can be customized for different platforms in RPM spec.

add_custom_target(pki-server-webapp-lib ALL
    COMMENT "Creating links for PKI webapp library")

add_custom_command(
    TARGET pki-server-webapp-lib
    COMMAND ${CMAKE_COMMAND} -E make_directory webapp/lib
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${SLF4J_API_JAR} webapp/lib/slf4j-api.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${SLF4J_JDK14_JAR} webapp/lib/slf4j-jdk14.jar
    COMMAND ln -sf /usr/share/java/pki/pki-certsrv.jar ${CMAKE_CURRENT_BINARY_DIR}/webapp/lib/pki-certsrv.jar
    COMMAND ln -sf /usr/share/java/pki/pki-cms.jar ${CMAKE_CURRENT_BINARY_DIR}/webapp/lib/pki-cms.jar
)

add_custom_target(pki-server-man ALL
    COMMENT "Creating PKI server manuals")

add_custom_command(
    TARGET pki-server-man
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_BINARY_DIR}/man/man1
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man1/pkidaemon.1.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man1/pkidaemon.1
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_BINARY_DIR}/man/man5
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man5/pki_default.cfg.5.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man5/pki_default.cfg.5
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man5/pki-server-logging.5.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man5/pki-server-logging.5
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man5/pki_healthcheck.conf.5.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man5/pki_healthcheck.conf.5
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_BINARY_DIR}/man/man8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server-acme.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server-acme.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server-ca.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server-ca.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server-cert.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server-cert.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server-instance.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server-instance.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server-kra.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server-kra.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server-migrate.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server-migrate.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server-nuxwdog.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server-nuxwdog.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server-ocsp.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server-ocsp.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server-subsystem.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server-subsystem.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server-tks.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server-tks.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server-tps.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server-tps.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-server-upgrade.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-server-upgrade.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pkidestroy.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pkidestroy.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pkispawn.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pkispawn.8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-healthcheck.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-healthcheck.8
)

# Customize default tomcat.conf.
configure_file(
    ${CMAKE_CURRENT_SOURCE_DIR}/share/etc/tomcat.conf
    ${CMAKE_CURRENT_BINARY_DIR}/share/etc/tomcat.conf
)

install(
    FILES
        ${CMAKE_BINARY_DIR}/dist/pki-cms.jar
    DESTINATION
        ${JAVA_JAR_INSTALL_DIR}/pki
)

install(
    DIRECTORY
        bin/
    DESTINATION
        ${DATA_INSTALL_DIR}/server/bin/
    FILE_PERMISSIONS
        OWNER_EXECUTE OWNER_READ
        GROUP_EXECUTE GROUP_READ
        WORLD_EXECUTE WORLD_READ
)

install(
    DIRECTORY
        certs/
    DESTINATION
        ${DATA_INSTALL_DIR}/server/certs
)

install(
    DIRECTORY
        examples/
    DESTINATION
        ${DATA_INSTALL_DIR}/server/examples
)

install(
    FILES
         ${CMAKE_CURRENT_BINARY_DIR}/share/etc/tomcat.conf
    DESTINATION
        ${DATA_INSTALL_DIR}/etc/
)

install(
    DIRECTORY
    DESTINATION
        ${VAR_INSTALL_DIR}/lib/pki
)

install(
    DIRECTORY
        ${CMAKE_CURRENT_BINARY_DIR}/lib/
    DESTINATION
        ${DATA_INSTALL_DIR}/server/lib
)

install(
    DIRECTORY
        ${CMAKE_CURRENT_BINARY_DIR}/common/lib/
    DESTINATION
        ${DATA_INSTALL_DIR}/server/common/lib
)

install(
    DIRECTORY
        ${CMAKE_CURRENT_BINARY_DIR}/webapp/lib/
    DESTINATION
        ${DATA_INSTALL_DIR}/server/webapps/pki/WEB-INF/lib
)

install(
    DIRECTORY
        ../../docs/
    DESTINATION
        ${DATA_INSTALL_DIR}/server/docs
)

# install man pages generated by md2man
install(
    DIRECTORY
        ${CMAKE_CURRENT_BINARY_DIR}/man/
    DESTINATION
        ${MAN_INSTALL_DIR}
    FILE_PERMISSIONS
        OWNER_READ OWNER_WRITE
        GROUP_READ
        WORLD_READ
)

# install Python libraries
install(
    DIRECTORY
        python/pki/server
    DESTINATION
        ${PYTHON3_SITE_PACKAGES}/pki
    FILES_MATCHING PATTERN
        *.py
)

# install systemd scripts
install(
    DIRECTORY
        share/lib/systemd/system/
    DESTINATION
        ${SYSTEMD_LIB_INSTALL_DIR}
    FILE_PERMISSIONS
        OWNER_WRITE OWNER_READ
        GROUP_READ
        WORLD_READ
)

install(
    FILES
        setup/CertServer.directory
        setup/menu.xml
        setup/pkidaemon_registry
    DESTINATION
        ${DATA_INSTALL_DIR}/setup/
    PERMISSIONS
        OWNER_WRITE OWNER_READ
        GROUP_READ
        WORLD_READ
)

# install executables
install(
    FILES
        ${CMAKE_CURRENT_SOURCE_DIR}/sbin/pki-server
        ${CMAKE_CURRENT_SOURCE_DIR}/sbin/pki-server-upgrade
        ${CMAKE_CURRENT_SOURCE_DIR}/sbin/pkidestroy
        ${CMAKE_CURRENT_SOURCE_DIR}/sbin/pkispawn
    DESTINATION
        ${SBIN_INSTALL_DIR}
    PERMISSIONS
        OWNER_EXECUTE OWNER_WRITE OWNER_READ
        GROUP_EXECUTE GROUP_READ
        WORLD_EXECUTE WORLD_READ
)

install(
    DIRECTORY
        upgrade
    DESTINATION
        ${DATA_INSTALL_DIR}/server/
    FILE_PERMISSIONS
        OWNER_WRITE OWNER_READ
        GROUP_READ
        WORLD_READ
    PATTERN ".gitignore" EXCLUDE
)

install(
    FILES
        scripts/pkidaemon
        scripts/pki-server-nuxwdog
    DESTINATION
        ${BIN_INSTALL_DIR}
    PERMISSIONS
        OWNER_EXECUTE OWNER_WRITE OWNER_READ
        GROUP_EXECUTE GROUP_READ
        WORLD_EXECUTE WORLD_READ
)

install(
    FILES
        scripts/operations
    DESTINATION
        ${DATA_INSTALL_DIR}/scripts/
    PERMISSIONS
        OWNER_EXECUTE OWNER_WRITE OWNER_READ
        GROUP_EXECUTE GROUP_READ
        WORLD_EXECUTE WORLD_READ
)

install(
    DIRECTORY
        config
    DESTINATION
        ${DATA_INSTALL_DIR}/deployment
)

# install server files
install(
    DIRECTORY
        share/
    DESTINATION
        ${DATA_INSTALL_DIR}/server/
    PATTERN
        "lib/*" EXCLUDE
    PATTERN
        "etc/*" EXCLUDE
)

# install server configuration files
install(
    DIRECTORY
        etc
    DESTINATION
        ${DATA_INSTALL_DIR}/server
)

# install empty directories
install(CODE "file(MAKE_DIRECTORY \$ENV{DESTDIR}${VAR_INSTALL_DIR}/lock/pki)")
install(CODE "file(MAKE_DIRECTORY \$ENV{DESTDIR}${VAR_INSTALL_DIR}/run/pki)")
