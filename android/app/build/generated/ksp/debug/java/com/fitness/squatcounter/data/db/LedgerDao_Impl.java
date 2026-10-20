package com.fitness.squatcounter.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.fitness.squatcounter.data.LedgerEntryType;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class LedgerDao_Impl implements LedgerDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LedgerEntryEntity> __insertionAdapterOfLedgerEntryEntity;

  public LedgerDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLedgerEntryEntity = new EntityInsertionAdapter<LedgerEntryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `ledger_entries` (`id`,`sessionId`,`repIndex`,`entryType`,`amountSeconds`,`exerciseType`,`wallClockTimeMs`,`elapsedRealtimeMs`,`bootCount`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LedgerEntryEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getSessionId());
        if (entity.getRepIndex() == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, entity.getRepIndex());
        }
        statement.bindString(4, __LedgerEntryType_enumToString(entity.getEntryType()));
        statement.bindLong(5, entity.getAmountSeconds());
        if (entity.getExerciseType() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getExerciseType());
        }
        statement.bindLong(7, entity.getWallClockTimeMs());
        statement.bindLong(8, entity.getElapsedRealtimeMs());
        statement.bindLong(9, entity.getBootCount());
      }
    };
  }

  @Override
  public Object insertEntry(final LedgerEntryEntity entry,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfLedgerEntryEntity.insertAndReturnId(entry);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllEntries(final Continuation<? super List<LedgerEntryEntity>> $completion) {
    final String _sql = "SELECT * FROM ledger_entries ORDER BY id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LedgerEntryEntity>>() {
      @Override
      @NonNull
      public List<LedgerEntryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfRepIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "repIndex");
          final int _cursorIndexOfEntryType = CursorUtil.getColumnIndexOrThrow(_cursor, "entryType");
          final int _cursorIndexOfAmountSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "amountSeconds");
          final int _cursorIndexOfExerciseType = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseType");
          final int _cursorIndexOfWallClockTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "wallClockTimeMs");
          final int _cursorIndexOfElapsedRealtimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "elapsedRealtimeMs");
          final int _cursorIndexOfBootCount = CursorUtil.getColumnIndexOrThrow(_cursor, "bootCount");
          final List<LedgerEntryEntity> _result = new ArrayList<LedgerEntryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LedgerEntryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final Integer _tmpRepIndex;
            if (_cursor.isNull(_cursorIndexOfRepIndex)) {
              _tmpRepIndex = null;
            } else {
              _tmpRepIndex = _cursor.getInt(_cursorIndexOfRepIndex);
            }
            final LedgerEntryType _tmpEntryType;
            _tmpEntryType = __LedgerEntryType_stringToEnum(_cursor.getString(_cursorIndexOfEntryType));
            final int _tmpAmountSeconds;
            _tmpAmountSeconds = _cursor.getInt(_cursorIndexOfAmountSeconds);
            final String _tmpExerciseType;
            if (_cursor.isNull(_cursorIndexOfExerciseType)) {
              _tmpExerciseType = null;
            } else {
              _tmpExerciseType = _cursor.getString(_cursorIndexOfExerciseType);
            }
            final long _tmpWallClockTimeMs;
            _tmpWallClockTimeMs = _cursor.getLong(_cursorIndexOfWallClockTimeMs);
            final long _tmpElapsedRealtimeMs;
            _tmpElapsedRealtimeMs = _cursor.getLong(_cursorIndexOfElapsedRealtimeMs);
            final int _tmpBootCount;
            _tmpBootCount = _cursor.getInt(_cursorIndexOfBootCount);
            _item = new LedgerEntryEntity(_tmpId,_tmpSessionId,_tmpRepIndex,_tmpEntryType,_tmpAmountSeconds,_tmpExerciseType,_tmpWallClockTimeMs,_tmpElapsedRealtimeMs,_tmpBootCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<LedgerEntryEntity>> getAllEntriesFlow() {
    final String _sql = "SELECT * FROM ledger_entries ORDER BY id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"ledger_entries"}, new Callable<List<LedgerEntryEntity>>() {
      @Override
      @NonNull
      public List<LedgerEntryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfRepIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "repIndex");
          final int _cursorIndexOfEntryType = CursorUtil.getColumnIndexOrThrow(_cursor, "entryType");
          final int _cursorIndexOfAmountSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "amountSeconds");
          final int _cursorIndexOfExerciseType = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseType");
          final int _cursorIndexOfWallClockTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "wallClockTimeMs");
          final int _cursorIndexOfElapsedRealtimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "elapsedRealtimeMs");
          final int _cursorIndexOfBootCount = CursorUtil.getColumnIndexOrThrow(_cursor, "bootCount");
          final List<LedgerEntryEntity> _result = new ArrayList<LedgerEntryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LedgerEntryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final Integer _tmpRepIndex;
            if (_cursor.isNull(_cursorIndexOfRepIndex)) {
              _tmpRepIndex = null;
            } else {
              _tmpRepIndex = _cursor.getInt(_cursorIndexOfRepIndex);
            }
            final LedgerEntryType _tmpEntryType;
            _tmpEntryType = __LedgerEntryType_stringToEnum(_cursor.getString(_cursorIndexOfEntryType));
            final int _tmpAmountSeconds;
            _tmpAmountSeconds = _cursor.getInt(_cursorIndexOfAmountSeconds);
            final String _tmpExerciseType;
            if (_cursor.isNull(_cursorIndexOfExerciseType)) {
              _tmpExerciseType = null;
            } else {
              _tmpExerciseType = _cursor.getString(_cursorIndexOfExerciseType);
            }
            final long _tmpWallClockTimeMs;
            _tmpWallClockTimeMs = _cursor.getLong(_cursorIndexOfWallClockTimeMs);
            final long _tmpElapsedRealtimeMs;
            _tmpElapsedRealtimeMs = _cursor.getLong(_cursorIndexOfElapsedRealtimeMs);
            final int _tmpBootCount;
            _tmpBootCount = _cursor.getInt(_cursorIndexOfBootCount);
            _item = new LedgerEntryEntity(_tmpId,_tmpSessionId,_tmpRepIndex,_tmpEntryType,_tmpAmountSeconds,_tmpExerciseType,_tmpWallClockTimeMs,_tmpElapsedRealtimeMs,_tmpBootCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getLatestEntry(final Continuation<? super LedgerEntryEntity> $completion) {
    final String _sql = "SELECT * FROM ledger_entries ORDER BY id DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LedgerEntryEntity>() {
      @Override
      @Nullable
      public LedgerEntryEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfRepIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "repIndex");
          final int _cursorIndexOfEntryType = CursorUtil.getColumnIndexOrThrow(_cursor, "entryType");
          final int _cursorIndexOfAmountSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "amountSeconds");
          final int _cursorIndexOfExerciseType = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseType");
          final int _cursorIndexOfWallClockTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "wallClockTimeMs");
          final int _cursorIndexOfElapsedRealtimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "elapsedRealtimeMs");
          final int _cursorIndexOfBootCount = CursorUtil.getColumnIndexOrThrow(_cursor, "bootCount");
          final LedgerEntryEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final Integer _tmpRepIndex;
            if (_cursor.isNull(_cursorIndexOfRepIndex)) {
              _tmpRepIndex = null;
            } else {
              _tmpRepIndex = _cursor.getInt(_cursorIndexOfRepIndex);
            }
            final LedgerEntryType _tmpEntryType;
            _tmpEntryType = __LedgerEntryType_stringToEnum(_cursor.getString(_cursorIndexOfEntryType));
            final int _tmpAmountSeconds;
            _tmpAmountSeconds = _cursor.getInt(_cursorIndexOfAmountSeconds);
            final String _tmpExerciseType;
            if (_cursor.isNull(_cursorIndexOfExerciseType)) {
              _tmpExerciseType = null;
            } else {
              _tmpExerciseType = _cursor.getString(_cursorIndexOfExerciseType);
            }
            final long _tmpWallClockTimeMs;
            _tmpWallClockTimeMs = _cursor.getLong(_cursorIndexOfWallClockTimeMs);
            final long _tmpElapsedRealtimeMs;
            _tmpElapsedRealtimeMs = _cursor.getLong(_cursorIndexOfElapsedRealtimeMs);
            final int _tmpBootCount;
            _tmpBootCount = _cursor.getInt(_cursorIndexOfBootCount);
            _result = new LedgerEntryEntity(_tmpId,_tmpSessionId,_tmpRepIndex,_tmpEntryType,_tmpAmountSeconds,_tmpExerciseType,_tmpWallClockTimeMs,_tmpElapsedRealtimeMs,_tmpBootCount);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getEarnedSecondsBetween(final long startOfDayMs, final long endOfDayMs,
      final Continuation<? super Integer> $completion) {
    final String _sql = "\n"
            + "        SELECT COALESCE(SUM(amountSeconds), 0) FROM ledger_entries \n"
            + "        WHERE entryType = 'EARN' AND wallClockTimeMs >= ? AND wallClockTimeMs <= ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startOfDayMs);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endOfDayMs);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getEarnedSecondsForExerciseBetween(final long startOfDayMs, final long endOfDayMs,
      final String exerciseType, final Continuation<? super Integer> $completion) {
    final String _sql = "\n"
            + "        SELECT COALESCE(SUM(amountSeconds), 0) FROM ledger_entries \n"
            + "        WHERE entryType = 'EARN' AND exerciseType = ? \n"
            + "        AND wallClockTimeMs >= ? AND wallClockTimeMs <= ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, exerciseType);
    _argIndex = 2;
    _statement.bindLong(_argIndex, startOfDayMs);
    _argIndex = 3;
    _statement.bindLong(_argIndex, endOfDayMs);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getEntriesForSession(final String sessionId,
      final Continuation<? super List<LedgerEntryEntity>> $completion) {
    final String _sql = "SELECT * FROM ledger_entries WHERE sessionId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LedgerEntryEntity>>() {
      @Override
      @NonNull
      public List<LedgerEntryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfRepIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "repIndex");
          final int _cursorIndexOfEntryType = CursorUtil.getColumnIndexOrThrow(_cursor, "entryType");
          final int _cursorIndexOfAmountSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "amountSeconds");
          final int _cursorIndexOfExerciseType = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseType");
          final int _cursorIndexOfWallClockTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "wallClockTimeMs");
          final int _cursorIndexOfElapsedRealtimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "elapsedRealtimeMs");
          final int _cursorIndexOfBootCount = CursorUtil.getColumnIndexOrThrow(_cursor, "bootCount");
          final List<LedgerEntryEntity> _result = new ArrayList<LedgerEntryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LedgerEntryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final Integer _tmpRepIndex;
            if (_cursor.isNull(_cursorIndexOfRepIndex)) {
              _tmpRepIndex = null;
            } else {
              _tmpRepIndex = _cursor.getInt(_cursorIndexOfRepIndex);
            }
            final LedgerEntryType _tmpEntryType;
            _tmpEntryType = __LedgerEntryType_stringToEnum(_cursor.getString(_cursorIndexOfEntryType));
            final int _tmpAmountSeconds;
            _tmpAmountSeconds = _cursor.getInt(_cursorIndexOfAmountSeconds);
            final String _tmpExerciseType;
            if (_cursor.isNull(_cursorIndexOfExerciseType)) {
              _tmpExerciseType = null;
            } else {
              _tmpExerciseType = _cursor.getString(_cursorIndexOfExerciseType);
            }
            final long _tmpWallClockTimeMs;
            _tmpWallClockTimeMs = _cursor.getLong(_cursorIndexOfWallClockTimeMs);
            final long _tmpElapsedRealtimeMs;
            _tmpElapsedRealtimeMs = _cursor.getLong(_cursorIndexOfElapsedRealtimeMs);
            final int _tmpBootCount;
            _tmpBootCount = _cursor.getInt(_cursorIndexOfBootCount);
            _item = new LedgerEntryEntity(_tmpId,_tmpSessionId,_tmpRepIndex,_tmpEntryType,_tmpAmountSeconds,_tmpExerciseType,_tmpWallClockTimeMs,_tmpElapsedRealtimeMs,_tmpBootCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private String __LedgerEntryType_enumToString(@NonNull final LedgerEntryType _value) {
    switch (_value) {
      case EARN: return "EARN";
      case SPEND: return "SPEND";
      case EXPIRE: return "EXPIRE";
      case ADJUST: return "ADJUST";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private LedgerEntryType __LedgerEntryType_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "EARN": return LedgerEntryType.EARN;
      case "SPEND": return LedgerEntryType.SPEND;
      case "EXPIRE": return LedgerEntryType.EXPIRE;
      case "ADJUST": return LedgerEntryType.ADJUST;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }
}
