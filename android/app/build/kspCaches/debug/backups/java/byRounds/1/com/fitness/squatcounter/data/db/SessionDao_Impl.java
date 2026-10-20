package com.fitness.squatcounter.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.fitness.squatcounter.data.SessionStatus;
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
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SessionDao_Impl implements SessionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SessionEntity> __insertionAdapterOfSessionEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateSessionStatus;

  private final SharedSQLiteStatement __preparedStmtOfIncrementValidReps;

  private final SharedSQLiteStatement __preparedStmtOfIncrementRejectedReps;

  private final SharedSQLiteStatement __preparedStmtOfCloseStaleSessions;

  public SessionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSessionEntity = new EntityInsertionAdapter<SessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `sessions` (`sessionId`,`exerciseType`,`startTimeMs`,`endTimeMs`,`status`,`validReps`,`rejectedReps`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SessionEntity entity) {
        statement.bindString(1, entity.getSessionId());
        statement.bindString(2, entity.getExerciseType());
        statement.bindLong(3, entity.getStartTimeMs());
        if (entity.getEndTimeMs() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getEndTimeMs());
        }
        statement.bindString(5, __SessionStatus_enumToString(entity.getStatus()));
        statement.bindLong(6, entity.getValidReps());
        statement.bindLong(7, entity.getRejectedReps());
      }
    };
    this.__preparedStmtOfUpdateSessionStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE sessions SET status = ?, endTimeMs = ? WHERE sessionId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfIncrementValidReps = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE sessions SET validReps = validReps + 1 WHERE sessionId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfIncrementRejectedReps = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE sessions SET rejectedReps = rejectedReps + 1 WHERE sessionId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfCloseStaleSessions = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE sessions SET status = ?, endTimeMs = ? WHERE endTimeMs IS NULL AND sessionId != ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertSession(final SessionEntity session,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfSessionEntity.insertAndReturnId(session);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateSessionStatus(final String sessionId, final SessionStatus status,
      final long endTimeMs, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateSessionStatus.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, __SessionStatus_enumToString(status));
        _argIndex = 2;
        _stmt.bindLong(_argIndex, endTimeMs);
        _argIndex = 3;
        _stmt.bindString(_argIndex, sessionId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateSessionStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object incrementValidReps(final String sessionId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfIncrementValidReps.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, sessionId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfIncrementValidReps.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object incrementRejectedReps(final String sessionId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfIncrementRejectedReps.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, sessionId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfIncrementRejectedReps.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object closeStaleSessions(final long endTimeMs, final String excludeSessionId,
      final SessionStatus interruptedStatus, final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfCloseStaleSessions.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, __SessionStatus_enumToString(interruptedStatus));
        _argIndex = 2;
        _stmt.bindLong(_argIndex, endTimeMs);
        _argIndex = 3;
        _stmt.bindString(_argIndex, excludeSessionId);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfCloseStaleSessions.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getSession(final String sessionId,
      final Continuation<? super SessionEntity> $completion) {
    final String _sql = "SELECT * FROM sessions WHERE sessionId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SessionEntity>() {
      @Override
      @Nullable
      public SessionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfExerciseType = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseType");
          final int _cursorIndexOfStartTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "startTimeMs");
          final int _cursorIndexOfEndTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "endTimeMs");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfValidReps = CursorUtil.getColumnIndexOrThrow(_cursor, "validReps");
          final int _cursorIndexOfRejectedReps = CursorUtil.getColumnIndexOrThrow(_cursor, "rejectedReps");
          final SessionEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpExerciseType;
            _tmpExerciseType = _cursor.getString(_cursorIndexOfExerciseType);
            final long _tmpStartTimeMs;
            _tmpStartTimeMs = _cursor.getLong(_cursorIndexOfStartTimeMs);
            final Long _tmpEndTimeMs;
            if (_cursor.isNull(_cursorIndexOfEndTimeMs)) {
              _tmpEndTimeMs = null;
            } else {
              _tmpEndTimeMs = _cursor.getLong(_cursorIndexOfEndTimeMs);
            }
            final SessionStatus _tmpStatus;
            _tmpStatus = __SessionStatus_stringToEnum(_cursor.getString(_cursorIndexOfStatus));
            final int _tmpValidReps;
            _tmpValidReps = _cursor.getInt(_cursorIndexOfValidReps);
            final int _tmpRejectedReps;
            _tmpRejectedReps = _cursor.getInt(_cursorIndexOfRejectedReps);
            _result = new SessionEntity(_tmpSessionId,_tmpExerciseType,_tmpStartTimeMs,_tmpEndTimeMs,_tmpStatus,_tmpValidReps,_tmpRejectedReps);
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
  public Object getAllSessions(final Continuation<? super List<SessionEntity>> $completion) {
    final String _sql = "SELECT * FROM sessions ORDER BY startTimeMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SessionEntity>>() {
      @Override
      @NonNull
      public List<SessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfExerciseType = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseType");
          final int _cursorIndexOfStartTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "startTimeMs");
          final int _cursorIndexOfEndTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "endTimeMs");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfValidReps = CursorUtil.getColumnIndexOrThrow(_cursor, "validReps");
          final int _cursorIndexOfRejectedReps = CursorUtil.getColumnIndexOrThrow(_cursor, "rejectedReps");
          final List<SessionEntity> _result = new ArrayList<SessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SessionEntity _item;
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpExerciseType;
            _tmpExerciseType = _cursor.getString(_cursorIndexOfExerciseType);
            final long _tmpStartTimeMs;
            _tmpStartTimeMs = _cursor.getLong(_cursorIndexOfStartTimeMs);
            final Long _tmpEndTimeMs;
            if (_cursor.isNull(_cursorIndexOfEndTimeMs)) {
              _tmpEndTimeMs = null;
            } else {
              _tmpEndTimeMs = _cursor.getLong(_cursorIndexOfEndTimeMs);
            }
            final SessionStatus _tmpStatus;
            _tmpStatus = __SessionStatus_stringToEnum(_cursor.getString(_cursorIndexOfStatus));
            final int _tmpValidReps;
            _tmpValidReps = _cursor.getInt(_cursorIndexOfValidReps);
            final int _tmpRejectedReps;
            _tmpRejectedReps = _cursor.getInt(_cursorIndexOfRejectedReps);
            _item = new SessionEntity(_tmpSessionId,_tmpExerciseType,_tmpStartTimeMs,_tmpEndTimeMs,_tmpStatus,_tmpValidReps,_tmpRejectedReps);
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

  private String __SessionStatus_enumToString(@NonNull final SessionStatus _value) {
    switch (_value) {
      case COMPLETED: return "COMPLETED";
      case INTERRUPTED: return "INTERRUPTED";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private SessionStatus __SessionStatus_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "COMPLETED": return SessionStatus.COMPLETED;
      case "INTERRUPTED": return SessionStatus.INTERRUPTED;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }
}
