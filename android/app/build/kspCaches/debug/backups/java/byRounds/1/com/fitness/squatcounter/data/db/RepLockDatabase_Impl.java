package com.fitness.squatcounter.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class RepLockDatabase_Impl extends RepLockDatabase {
  private volatile SessionDao _sessionDao;

  private volatile LedgerDao _ledgerDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sessions` (`sessionId` TEXT NOT NULL, `exerciseType` TEXT NOT NULL, `startTimeMs` INTEGER NOT NULL, `endTimeMs` INTEGER, `status` TEXT NOT NULL, `validReps` INTEGER NOT NULL, `rejectedReps` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `repIndex` INTEGER, `entryType` TEXT NOT NULL, `amountSeconds` INTEGER NOT NULL, `exerciseType` TEXT, `wallClockTimeMs` INTEGER NOT NULL, `elapsedRealtimeMs` INTEGER NOT NULL, `bootCount` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_entries_sessionId_repIndex` ON `ledger_entries` (`sessionId`, `repIndex`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_wallClockTimeMs` ON `ledger_entries` (`wallClockTimeMs`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_entryType` ON `ledger_entries` (`entryType`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ade07b720d851d7714b16d987bc91a1b')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `sessions`");
        db.execSQL("DROP TABLE IF EXISTS `ledger_entries`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsSessions = new HashMap<String, TableInfo.Column>(7);
        _columnsSessions.put("sessionId", new TableInfo.Column("sessionId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("exerciseType", new TableInfo.Column("exerciseType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("startTimeMs", new TableInfo.Column("startTimeMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("endTimeMs", new TableInfo.Column("endTimeMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("validReps", new TableInfo.Column("validReps", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("rejectedReps", new TableInfo.Column("rejectedReps", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSessions = new TableInfo("sessions", _columnsSessions, _foreignKeysSessions, _indicesSessions);
        final TableInfo _existingSessions = TableInfo.read(db, "sessions");
        if (!_infoSessions.equals(_existingSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "sessions(com.fitness.squatcounter.data.db.SessionEntity).\n"
                  + " Expected:\n" + _infoSessions + "\n"
                  + " Found:\n" + _existingSessions);
        }
        final HashMap<String, TableInfo.Column> _columnsLedgerEntries = new HashMap<String, TableInfo.Column>(9);
        _columnsLedgerEntries.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLedgerEntries.put("sessionId", new TableInfo.Column("sessionId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLedgerEntries.put("repIndex", new TableInfo.Column("repIndex", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLedgerEntries.put("entryType", new TableInfo.Column("entryType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLedgerEntries.put("amountSeconds", new TableInfo.Column("amountSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLedgerEntries.put("exerciseType", new TableInfo.Column("exerciseType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLedgerEntries.put("wallClockTimeMs", new TableInfo.Column("wallClockTimeMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLedgerEntries.put("elapsedRealtimeMs", new TableInfo.Column("elapsedRealtimeMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLedgerEntries.put("bootCount", new TableInfo.Column("bootCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLedgerEntries = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysLedgerEntries.add(new TableInfo.ForeignKey("sessions", "CASCADE", "NO ACTION", Arrays.asList("sessionId"), Arrays.asList("sessionId")));
        final HashSet<TableInfo.Index> _indicesLedgerEntries = new HashSet<TableInfo.Index>(3);
        _indicesLedgerEntries.add(new TableInfo.Index("index_ledger_entries_sessionId_repIndex", true, Arrays.asList("sessionId", "repIndex"), Arrays.asList("ASC", "ASC")));
        _indicesLedgerEntries.add(new TableInfo.Index("index_ledger_entries_wallClockTimeMs", false, Arrays.asList("wallClockTimeMs"), Arrays.asList("ASC")));
        _indicesLedgerEntries.add(new TableInfo.Index("index_ledger_entries_entryType", false, Arrays.asList("entryType"), Arrays.asList("ASC")));
        final TableInfo _infoLedgerEntries = new TableInfo("ledger_entries", _columnsLedgerEntries, _foreignKeysLedgerEntries, _indicesLedgerEntries);
        final TableInfo _existingLedgerEntries = TableInfo.read(db, "ledger_entries");
        if (!_infoLedgerEntries.equals(_existingLedgerEntries)) {
          return new RoomOpenHelper.ValidationResult(false, "ledger_entries(com.fitness.squatcounter.data.db.LedgerEntryEntity).\n"
                  + " Expected:\n" + _infoLedgerEntries + "\n"
                  + " Found:\n" + _existingLedgerEntries);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "ade07b720d851d7714b16d987bc91a1b", "7e0232279c5f00a32b08f0b113a66d9c");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "sessions","ledger_entries");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `sessions`");
      _db.execSQL("DELETE FROM `ledger_entries`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(SessionDao.class, SessionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(LedgerDao.class, LedgerDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public SessionDao sessionDao() {
    if (_sessionDao != null) {
      return _sessionDao;
    } else {
      synchronized(this) {
        if(_sessionDao == null) {
          _sessionDao = new SessionDao_Impl(this);
        }
        return _sessionDao;
      }
    }
  }

  @Override
  public LedgerDao ledgerDao() {
    if (_ledgerDao != null) {
      return _ledgerDao;
    } else {
      synchronized(this) {
        if(_ledgerDao == null) {
          _ledgerDao = new LedgerDao_Impl(this);
        }
        return _ledgerDao;
      }
    }
  }
}
