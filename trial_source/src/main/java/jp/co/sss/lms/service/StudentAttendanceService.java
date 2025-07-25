package jp.co.sss.lms.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		// ▼ここから追記▼
		attendanceForm.setHours(attendanceUtil.getHoursMap());
		attendanceForm.setMinutes(attendanceUtil.getMinutesMap());
		// ▲ここまで追記▲

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			// ▼ここから追記
			dailyAttendanceForm.setTrainingStartHour(attendanceUtil.extractHour(attendanceManagementDto.getTrainingStartTime()));
			dailyAttendanceForm.setTrainingStartMinute(attendanceUtil.extractMinute(attendanceManagementDto.getTrainingStartTime()));
			// ▲ここまで追記
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			// ▼ここから追記
			dailyAttendanceForm.setTrainingEndHour(attendanceUtil.extractHour(attendanceManagementDto.getTrainingEndTime()));
			dailyAttendanceForm.setTrainingEndMinute(attendanceUtil.extractMinute(attendanceManagementDto.getTrainingEndTime()));
			// ▲ここまで追記
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}
//25
	public Integer getNotEnterCount(Integer lmsUserId) {
			try {
				SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
				Date trainingDate = df.parse(df.format(new Date()));
				Integer notEnterCount = tStudentAttendanceMapper.notEnterCount(lmsUserId, trainingDate, Constants.DB_FLG_FALSE);
	        return notEnterCount;
	    } catch (ParseException e) {
	        e.printStackTrace();
	        return 0;
	    }
	}
	
	/**
	 * 勤怠登録・更新処理
	 * * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = null; 
            // 既存の勤怠情報を検索または新規作成するロジックをここに追加
            if (dailyAttendanceForm.getStudentAttendanceId() != null) {
                for (TStudentAttendance existingAttendance : tStudentAttendanceList) {
                    if (existingAttendance.getStudentAttendanceId().equals(dailyAttendanceForm.getStudentAttendanceId())) {
                        tStudentAttendance = existingAttendance;
                        break;
                    }
                }
            }
            if (tStudentAttendance == null) {
                tStudentAttendance = new TStudentAttendance();
                // 新規登録の場合の初期作成ユーザーと日時を設定
            }

            // 日次勤怠フォームから更新用のエンティティにコピー
            // trainingStartTime, trainingEndTime, status は後で独自に設定するため、コピー対象から除外
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance, "trainingStartTime", "trainingEndTime", "status");

			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());

			// ▼ここから追記（元の「出勤時刻整形」「退勤時刻整形」の行を削除して差し込み
			// 出勤時刻を時と分から結合
			String trainingStartTimeStr = "";
			if (dailyAttendanceForm.getTrainingStartHour() != null && dailyAttendanceForm.getTrainingStartMinute() != null) {
			    trainingStartTimeStr = String.format("%02d:%02d",
			            dailyAttendanceForm.getTrainingStartHour(),
			            dailyAttendanceForm.getTrainingStartMinute());
			}
			tStudentAttendance.setTrainingStartTime(trainingStartTimeStr);

			// 退勤時刻を時と分から結合
			String trainingEndTimeStr = "";
			if (dailyAttendanceForm.getTrainingEndHour() != null && dailyAttendanceForm.getTrainingEndMinute() != null) {
			    trainingEndTimeStr = String.format("%02d:%02d",
			            dailyAttendanceForm.getTrainingEndHour(),
			            dailyAttendanceForm.getTrainingEndMinute());
			}
			tStudentAttendance.setTrainingEndTime(trainingEndTimeStr);
			// ▲ここまで追記

			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());

			// ▼ここから追記（既存の遅刻早退ステータス判定ロジックのブロックを削除して差し込み）
			TrainingTime parsedTrainingStartTime = null;
			if (!trainingStartTimeStr.isEmpty()) {
			    parsedTrainingStartTime = new TrainingTime(trainingStartTimeStr);
			}
			TrainingTime parsedTrainingEndTime = null;
			if (!trainingEndTimeStr.isEmpty()) {
			    parsedTrainingEndTime = new TrainingTime(trainingEndTimeStr);
			}

			if ((parsedTrainingStartTime != null || parsedTrainingEndTime != null) && !"欠席".equals(dailyAttendanceForm.getStatusDispName())) {
			    AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(parsedTrainingStartTime, parsedTrainingEndTime);
			    tStudentAttendance.setStatus(attendanceStatusEnum.code);
			} else if ("欠席".equals(dailyAttendanceForm.getStatusDispName())) {
			    tStudentAttendance.setStatus(AttendanceStatusEnum.ABSENT.code);
			} else { // どちらも入力がない場合はステータスをなしに
			    tStudentAttendance.setStatus(AttendanceStatusEnum.NONE.code);
			}
			// ▲ここまで追記

			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			
            // ▼ここから追記（登録・更新処理の調整）
            if (tStudentAttendance.getStudentAttendanceId() == null) {
                // 新規登録の場合のみ、初回作成ユーザーと日時を設定
                tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
                tStudentAttendance.setFirstCreateDate(date);
                tStudentAttendanceMapper.insert(tStudentAttendance);
            } else {
                tStudentAttendanceMapper.update(tStudentAttendance);
            }
            // ▲ここまで追記
		}

		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

}


