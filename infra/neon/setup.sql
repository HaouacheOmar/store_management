-- Neon : une base et un rôle par service (équivalent de infra/postgres/init-db.sh pour Docker).
-- À exécuter une fois, connecté à la base « neondb » avec le rôle propriétaire du projet :
--   psql "<chaîne de connexion neondb_owner>" -v pw_auth=... -v pw_catalog=... -v pw_supplier=... \
--        -v pw_sales=... -v pw_reporting=... -f infra/neon/setup.sql
-- Mots de passe : longs et aléatoires (Neon refuse les mots de passe faibles), un par service.
-- Les tables sont créées ensuite par Flyway, au premier démarrage de chaque service.

CREATE ROLE auth_user      LOGIN PASSWORD :'pw_auth';
CREATE ROLE catalog_user   LOGIN PASSWORD :'pw_catalog';
CREATE ROLE supplier_user  LOGIN PASSWORD :'pw_supplier';
CREATE ROLE sales_user     LOGIN PASSWORD :'pw_sales';
CREATE ROLE reporting_user LOGIN PASSWORD :'pw_reporting';

-- Postgres 16 : pour donner une base à un rôle (OWNER), il faut en être membre
GRANT auth_user, catalog_user, supplier_user, sales_user, reporting_user TO CURRENT_USER;

CREATE DATABASE auth_db      OWNER auth_user;
CREATE DATABASE catalog_db   OWNER catalog_user;
CREATE DATABASE supplier_db  OWNER supplier_user;
CREATE DATABASE sales_db     OWNER sales_user;
CREATE DATABASE reporting_db OWNER reporting_user;
