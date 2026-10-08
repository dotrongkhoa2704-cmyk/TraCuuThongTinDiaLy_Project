"""Run only against a disposable MySQL/MariaDB instance: python tests/database_schema_test.py MYSQL_EXE PORT."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
schema = "geo_schema_v14_test"
command = [sys.argv[1], "--no-defaults", "--protocol=TCP", "--host=127.0.0.1",
           "--port=" + sys.argv[2], "--user=root", "--default-character-set=utf8mb4", "--batch", "--skip-column-names"]


def sql(statement, error=None):
    result = subprocess.run(command, input=statement.encode("utf-8"), capture_output=True)
    if error:
        assert result.returncode and error in result.stderr.decode("utf-8", errors="replace"), result.stderr
    else:
        assert result.returncode == 0, result.stderr.decode("utf-8", errors="replace")
    return result.stdout.decode("utf-8").strip()


fresh = (root / "database/tra_cuu_dia_ly.sql").read_text(encoding="utf-8").replace("`tra_cuu_dia_ly`", f"`{schema}`")
migration = (root / "database/cap_nhat_database.sql").read_text(encoding="utf-8").replace("`tra_cuu_dia_ly`", f"`{schema}`")
sql(fresh)
prefix = f"USE {schema};\n"
sql(prefix + "INSERT INTO dia_diem_da_luu (id_tai_khoan,ten_dia_diem,vi_do,kinh_do) VALUES (1,'Null city',NULL,NULL);")
sql(prefix + "INSERT INTO dia_diem_da_luu (id_tai_khoan,ten_dia_diem,vi_do,kinh_do) VALUES (1,'Null city',NULL,NULL);", "1062")
sql(prefix + "INSERT INTO theo_doi_thoi_tiet (id_tai_khoan,ten_dia_diem,dieu_kien_canh_bao) VALUES (1,'Ho Chi Minh','Mua lon va Bao');", "1062")
sql(prefix + "INSERT INTO theo_doi_thoi_tiet (id_tai_khoan,ten_dia_diem,dieu_kien_canh_bao) VALUES (2,'Ho Chi Minh','Mua lon va Bao');")
sql(prefix + """
INSERT INTO bo_nho_dem_thoi_tiet (cache_key,data_json,type,created_at,expires_at)
VALUES ('weather:key','{}','weather',NOW(),DATE_ADD(NOW(),INTERVAL 5 MINUTE));
INSERT INTO bo_nho_dem_thoi_tiet (cache_key,data_json,type,created_at,expires_at)
VALUES ('weather:key','{"temp":32}','weather',NOW(),DATE_ADD(NOW(),INTERVAL 10 MINUTE))
ON DUPLICATE KEY UPDATE data_json=VALUES(data_json),created_at=VALUES(created_at),expires_at=VALUES(expires_at);
""")
assert sql(prefix + "SELECT COUNT(*),data_json FROM bo_nho_dem_thoi_tiet WHERE cache_key='weather:key';") == '1\t{"temp":32}'
sql(prefix + "UPDATE bo_nho_dem_thoi_tiet SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE);")
assert sql(prefix + "SELECT COUNT(*) FROM bo_nho_dem_thoi_tiet WHERE expires_at > NOW();") == '0'

# Reconstruct the previous schema in the disposable DB, preserving rows for migration checks.
sql(prefix + """
ALTER TABLE dia_diem_da_luu ADD INDEX legacy_saved_owner (id_tai_khoan);
ALTER TABLE dia_diem_da_luu DROP INDEX uq_saved_location, DROP INDEX idx_saved_user_date,
 DROP COLUMN vi_do_key, DROP COLUMN kinh_do_key;
ALTER TABLE theo_doi_thoi_tiet ADD INDEX legacy_subscription_owner (id_tai_khoan);
ALTER TABLE theo_doi_thoi_tiet DROP INDEX uq_weather_subscription, DROP INDEX idx_subscription_user_date,
 MODIFY dieu_kien_canh_bao VARCHAR(200) DEFAULT 'thoi_tiet_xau';
ALTER TABLE lich_su_tra_cuu ADD INDEX legacy_history_owner (id_tai_khoan);
ALTER TABLE lich_su_tra_cuu DROP INDEX idx_history_user_date;
ALTER TABLE khach_san DROP INDEX idx_hotel_city_rating;
ALTER TABLE bo_nho_dem_thoi_tiet DROP INDEX idx_cache_expires, DROP INDEX idx_cache_type_expires,
 DROP COLUMN type,
 CHANGE cache_key khoa_bo_nho_dem VARCHAR(255) NOT NULL,
 CHANGE data_json du_lieu_json LONGTEXT NOT NULL,
 CHANGE created_at thoi_gian_tao DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CHANGE expires_at thoi_gian_het_han DATETIME NOT NULL;
INSERT INTO dia_diem_da_luu (id_tai_khoan,ten_dia_diem) VALUES (1,'Null city');
""")
sql(migration, "Duplicate saved locations")
assert sql(prefix + "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='vi_do_key';") == '0'
sql(prefix + """
DROP PROCEDURE migrate_database_v14;
DELETE FROM dia_diem_da_luu WHERE id=(SELECT newest FROM (SELECT MAX(id) newest FROM dia_diem_da_luu WHERE ten_dia_diem='Null city') t);
""")
sql(migration)
assert sql(prefix + "SELECT data_json,type FROM bo_nho_dem_thoi_tiet WHERE cache_key='weather:key';") == '{"temp":32}\tgeneric'
assert sql(prefix + "SELECT COUNT(*) FROM dia_diem_da_luu;") == '5'
print("Database schema test passed: uniqueness, NULL coordinates, expiry, upsert, migration and duplicate preflight.")
