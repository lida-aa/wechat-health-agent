-- OPTIONAL AND DESTRUCTIVE. Do not run until old data has been backed up and
-- health_record migration has been verified for every user.

DROP TABLE IF EXISTS admin_user_images;
DROP TABLE IF EXISTS admin_user_files;
DROP TABLE IF EXISTS admin_user_voices;
DROP TABLE IF EXISTS health_vital_record;
DROP TABLE IF EXISTS t_user;
DROP TABLE IF EXISTS tasks;
DROP TABLE IF EXISTS scheduled_tasks;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS tickets;
