/*
 * Copyright (C) 2010-2014 Thialfihar <thi@thialfihar.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thialfihar.android.apg.ui.widget;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Vector;

import org.spongycastle.bcpg.sig.KeyFlags;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPSecretKey;
import org.thialfihar.android.apg.Id;
import org.thialfihar.android.apg.R;
import org.thialfihar.android.apg.pgp.PgpKeyHelper;
import org.thialfihar.android.apg.util.Choice;

import android.annotation.TargetApi;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.beardedhen.androidbootstrap.BootstrapButton;

import org.thialfihar.android.apg.Id;
import org.thialfihar.android.apg.R;
import org.thialfihar.android.apg.pgp.Key;
import org.thialfihar.android.apg.pgp.Utils;
import org.thialfihar.android.apg.util.Choice;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Vector;

public class KeyEditor extends LinearLayout implements Editor, OnClickListener {
    class ExpiryDatePickerDialog extends DatePickerDialog {
        public ExpiryDatePickerDialog(Context context, OnDateSetListener callBack,
                                      int year, int monthOfYear, int dayOfMonth) {
            super(context, callBack, year, monthOfYear, dayOfMonth);
        }

        public void setTitle(CharSequence title) {
            // set fixed title
            // need to get the string manually here, else it'll look it up and call
            // the CharSequence version again, resulting in an endless loop
            super.setTitle(getContext().getString(R.string.expiry_date_dialog_title));
        }
    }

    private Key mKey;

    private EditorListener mEditorListener = null;

    private boolean mIsMasterKey;
    BootstrapButton mDeleteButton;
    TextView mAlgorithm;
    TextView mKeyId;
    TextView mCreationDate;
    BootstrapButton mExpiryDateButton;
    GregorianCalendar mCreatedDate;
    GregorianCalendar mExpiryDate;
    CheckBox mChkCertify;
    CheckBox mChkSign;
    CheckBox mChkEncrypt;
    CheckBox mChkAuthenticate;

    private int mDatePickerResultCount = 0;
    private DatePickerDialog.OnDateSetListener mExpiryDateSetListener =
        new DatePickerDialog.OnDateSetListener() {
        public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
            // Note: Ignore results after the first one - android sends multiples.
            if (mDatePickerResultCount++ == 0) {
                GregorianCalendar date = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
                date.set(year, monthOfYear, dayOfMonth);
                setExpiryDate(date);
            }
        }
    };

    public KeyEditor(Context context) {
        super(context);
    }

    public KeyEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        setDrawingCacheEnabled(true);
        setAlwaysDrawnWithCacheEnabled(true);

        mAlgorithm = (TextView) findViewById(R.id.algorithm);
        mKeyId = (TextView) findViewById(R.id.keyId);
        mCreationDate = (TextView) findViewById(R.id.creation);
        mExpiryDateButton = (BootstrapButton) findViewById(R.id.expiry);

        mDeleteButton = (BootstrapButton) findViewById(R.id.delete);
        mDeleteButton.setOnClickListener(this);
        mChkCertify =  (CheckBox) findViewById(R.id.chkCertify);
        mChkSign =  (CheckBox) findViewById(R.id.chkSign);
        mChkEncrypt =  (CheckBox) findViewById(R.id.chkEncrypt);
        mChkAuthenticate =  (CheckBox) findViewById(R.id.chkAuthenticate);

        setExpiryDate(null);

        mExpiryDateButton.setOnClickListener(new OnClickListener() {
            @TargetApi(11)
            public void onClick(View v) {
                GregorianCalendar date = mExpiryDate;
                if (date == null) {
                    date = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
                }
                /*
                 * Using custom DatePickerDialog which overrides the setTitle because
                 * the DatePickerDialog title is buggy (unix warparound bug).
                 * See: https://code.google.com/p/android/issues/detail?id=49066
                 */
                DatePickerDialog dialog = new ExpiryDatePickerDialog(getContext(),
                        mExpiryDateSetListener, date.get(Calendar.YEAR), date.get(Calendar.MONTH),
                        date.get(Calendar.DAY_OF_MONTH));
                mDatePickerResultCount = 0;
                dialog.setCancelable(true);
                dialog.setButton(Dialog.BUTTON_NEGATIVE,
                        getContext().getString(R.string.btn_no_date),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // Note: Ignore results after the first one - android sends multiples.
                                if (mDatePickerResultCount++ == 0) {
                                    setExpiryDate(null);
                                }
                            }
                        });

                // setCalendarViewShown() is supported from API 11 onwards.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                    // Hide calendarView in tablets because of the unix warparound bug.
                    dialog.getDatePicker().setCalendarViewShown(false);
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                    if (dialog != null && mCreatedDate != null) {
                        dialog.getDatePicker().setMinDate(mCreatedDate.getTime().getTime() +
                                                            DateUtils.DAY_IN_MILLIS);
                    } else {
                        //When created date isn't available
                        dialog.getDatePicker().setMinDate(date.getTime().getTime() +
                                                            DateUtils.DAY_IN_MILLIS);
                    }
                }

                dialog.show();
            }
        });

        super.onFinishInflate();
    }

    public void setCanBeEdited(boolean canBeEdited) {
        if (!canBeEdited) {
            mDeleteButton.setVisibility(View.INVISIBLE);
            mExpiryDateButton.setEnabled(false);
            mChkSign.setEnabled(false); //certify is always disabled
            mChkEncrypt.setEnabled(false);
            mChkAuthenticate.setEnabled(false);
        }
    }

    public void setValue(Key key, boolean isMasterKey, int usage) {
        mKey = key;

        mIsMasterKey = isMasterKey;
        if (mIsMasterKey) {
            mDeleteButton.setVisibility(View.INVISIBLE);
        }

        mAlgorithm.setText(key.getAlgorithmInfo());
        mKeyId.setText("0x" + Utils.toHex(key.getKeyId(), 16));

        Vector<Choice> choices = new Vector<Choice>();
        boolean isElGamalKey = (key.getAlgorithm() == Key.ELGAMAL_ENCRYPT);
        boolean isDSAKey = (key.getAlgorithm() == Key.DSA);
        if (isElGamalKey) {
            mChkSign.setVisibility(View.INVISIBLE);
            TableLayout table = (TableLayout)findViewById(R.id.table_keylayout);
            TableRow row = (TableRow)findViewById(R.id.row_sign);
            table.removeView(row);
        }
        if (isDSAKey) {
            mChkEncrypt.setVisibility(View.INVISIBLE);
            TableLayout table = (TableLayout)findViewById(R.id.table_keylayout);
            TableRow row = (TableRow)findViewById(R.id.row_encrypt);
            table.removeView(row);
        }
        if (!mIsMasterKey) {
            mChkCertify.setVisibility(View.INVISIBLE);
            TableLayout table = (TableLayout)findViewById(R.id.table_keylayout);
            TableRow row = (TableRow)findViewById(R.id.row_certify);
            table.removeView(row);
        } else {
            TextView mLabelUsage2= (TextView) findViewById(R.id.label_usage2);
            mLabelUsage2.setVisibility(View.INVISIBLE);
        }

        int selectId = 0;
        if (key.isMasterKey())
            mChkCertify.setChecked(PgpKeyHelper.isCertificationKey(key));
        mChkSign.setChecked(PgpKeyHelper.isSigningKey(key));
        mChkEncrypt.setChecked(PgpKeyHelper.isEncryptionKey(key));
        mChkAuthenticate.setChecked(PgpKeyHelper.isAuthenticationKey(key));
        // TODO: use usage argument?

        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.setTime(key.getCreationDate());
        setCreatedDate(cal);
        cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        Date expiryDate = key.getExpiryDate();
        if (expiryDate == null) {
            setExpiryDate(null);
        } else {
            cal.setTime(key.getExpiryDate());
            setExpiryDate(cal);
        }

    }

    public Key getValue() {
        return mKey;
    }

    public void onClick(View v) {
        final ViewGroup parent = (ViewGroup) getParent();
        if (v == mDeleteButton) {
            parent.removeView(this);
            if (mEditorListener != null) {
                mEditorListener.onDeleted(this);
            }
        }
    }

    public void setEditorListener(EditorListener listener) {
        mEditorListener = listener;
    }

    private void setCreatedDate(GregorianCalendar date) {
        mCreatedDate = date;
        if (date == null) {
            mCreationDate.setText(getContext().getString(R.string.none));
        } else {
            mCreationDate.setText(DateFormat.getDateInstance().format(date.getTime()));
        }
    }

    private void setExpiryDate(GregorianCalendar date) {
        mExpiryDate = date;
        if (date == null) {
            mExpiryDateButton.setText(getContext().getString(R.string.none));
        } else {
            mExpiryDateButton.setText(DateFormat.getDateInstance().format(date.getTime()));
        }
    }

    public GregorianCalendar getExpiryDate() {
        return mExpiryDate;
    }

    public int getUsage() {
        int result = 0; // TODO: preserve other flags
        if (mChkCertify.isChecked())
            result |= KeyFlags.CERTIFY_OTHER;
        if (mChkSign.isChecked()) //TODO: fix what happens when we remove sign flag from master - should still be able to certify
            result |= KeyFlags.SIGN_DATA;
        if (mChkEncrypt.isChecked())
            result |= KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE;
        if (mChkAuthenticate.isChecked())
            result |= KeyFlags.AUTHENTICATION;

        return result;
    }
}

