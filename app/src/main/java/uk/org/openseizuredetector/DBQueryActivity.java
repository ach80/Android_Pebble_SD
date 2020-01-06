package uk.org.openseizuredetector;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TimePicker;

import java.util.Calendar;

public class DBQueryActivity extends AppCompatActivity
    implements View.OnClickListener {
    String TAG = "DBQueryActivity";
    Button mDateBtn;
    Button mTimeBtn;
    Button mExportBtn;
    EditText mDateTxt;
    EditText mTimeTxt;
    EditText mDurationTxt;

    int mYear;
    int mMonth;
    int mDay;
    int mHour;
    int mMinute;
    double mDuration;

    OsdUtil mUtil;
    Handler mHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dbquery);

        mHandler = new Handler();
        mUtil = new OsdUtil(this, mHandler);

        mDateBtn = (Button)findViewById(R.id.dateBtn);
        mDateBtn.setOnClickListener(this);
        mTimeBtn = (Button)findViewById(R.id.timeBtn);
        mTimeBtn.setOnClickListener(this);
        mExportBtn = (Button)findViewById(R.id.exportBtn);
        mExportBtn.setOnClickListener(this);
        mDateTxt = (EditText)findViewById(R.id.endDateText);
        mTimeTxt = (EditText)findViewById(R.id.endTimeText);
        mDurationTxt = (EditText)findViewById(R.id.durationText);

        // Get Current Date
        final Calendar c = Calendar.getInstance();
        mYear = c.get(Calendar.YEAR);
        mMonth = c.get(Calendar.MONTH);
        mDay = c.get(Calendar.DAY_OF_MONTH);
        mHour = c.get(Calendar.HOUR_OF_DAY);
        mMinute = c.get(Calendar.MINUTE);

        mDateTxt.setText(String.format("%02d-%02d-%04d",mDay, mMonth+1, mYear));
        mTimeTxt.setText(String.format("%02d:%02d:%02d", mHour, mMinute, 00));
        mDuration = 2.0;
        mDurationTxt.setText(String.format("%03.1f", mDuration));

    }

    @Override
    public void onClick(View view) {
        Log.v(TAG, "onClick()");
        if (view == mDateBtn) {
            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                    new DatePickerDialog.OnDateSetListener() {
                        @Override
                        public void onDateSet(DatePicker view, int year,
                                              int monthOfYear, int dayOfMonth) {
                            mDay = dayOfMonth;
                            mMonth = monthOfYear;
                            mYear = year;
                            mDateTxt.setText(String.format("%02d-%02d-%04d",mDay, mMonth+1, mYear));
                        }
                    }, mYear, mMonth, mDay);
            datePickerDialog.show();
        }
        if (view == mTimeBtn) {
            // Launch Time Picker Dialog
            TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                    new TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(TimePicker view, int hourOfDay,
                                              int minute) {
                            mHour = hourOfDay;
                            mMinute = minute;
                            mTimeTxt.setText(String.format("%02d:%02d:%02d", mHour, mMinute, 00));
                        }
                    }, mHour, mMinute, false);
            timePickerDialog.show();
        }
        if (view == mExportBtn) {
            mDateTxt.setText(String.format("%02d-%02d-%04d",mDay, mMonth+1, mYear));
            mTimeTxt.setText(String.format("%02d:%02d:%02d", mHour, mMinute, 00));
            mDuration = Double.parseDouble(mDurationTxt.getText().toString());


            mUtil.showToast(String.format("EndDate=%s %s, Duration=%3.1f hrs",
                    mDateTxt.getText().toString(), mTimeTxt.getText().toString() ,mDuration));

        }
    }
}
