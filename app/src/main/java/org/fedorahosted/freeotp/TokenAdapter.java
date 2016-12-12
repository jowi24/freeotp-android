/*
 * FreeOTP
 *
 * Authors: Nathaniel McCallum <npmccallum@redhat.com>
 *
 * Copyright (C) 2013  Nathaniel McCallum, Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fedorahosted.freeotp;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.squareup.picasso.Picasso;

import org.fedorahosted.freeotp.edit.DeleteActivity;
import org.fedorahosted.freeotp.edit.EditActivity;

import java.util.HashMap;
import java.util.Map;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;

public class TokenAdapter extends BaseReorderableAdapter {
    private final TokenPersistence mTokenPersistence;
    private final LayoutInflater mLayoutInflater;
    private final ClipboardManager mClipMan;
    private final Map<String, TokenCode> mTokenCodes;

    public TokenAdapter(Context ctx) {
        mTokenPersistence = new TokenPersistence(ctx);
        mLayoutInflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mClipMan = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        mTokenCodes = new HashMap<String, TokenCode>();
        registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                mTokenCodes.clear();
            }

            @Override
            public void onInvalidated() {
                mTokenCodes.clear();
            }
        });
    }

    @Override
    public int getCount() {
        return mTokenPersistence.length();
    }

    @Override
    public Token getItem(int position) {
        return mTokenPersistence.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    protected void move(int fromPosition, int toPosition) {
        mTokenPersistence.move(fromPosition, toPosition);
        notifyDataSetChanged();
    }

    @Override
    protected void bindView(final View view, final int position) {
        final Context ctx = view.getContext();
        TokenLayout tl = (TokenLayout) view;
        final Token token = getItem(position);

        tl.bind(token, R.menu.token, new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Intent i;

                switch (item.getItemId()) {
                    case R.id.action_edit:
                        i = new Intent(ctx, EditActivity.class);
                        i.putExtra(EditActivity.EXTRA_POSITION, position);
                        ctx.startActivity(i);
                        break;

                    case R.id.action_export:
                        BitMatrix result;
                        try {
                            result = new MultiFormatWriter().encode(token.toString(),
                                    BarcodeFormat.QR_CODE, 800, 800, null);
                        } catch (IllegalArgumentException iae) {
                            // Unsupported format
                            break;
                        } catch (WriterException e) {
                            break;
                        }
                        int w = result.getWidth();
                        int h = result.getHeight();
                        int[] pixels = new int[w * h];
                        for (int y = 0; y < h; y++) {
                            int offset = y * w;
                            for (int x = 0; x < w; x++) {
                                pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
                            }
                        }
                        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        bitmap.setPixels(pixels, 0, 800, 0, 0, w, h);

                        ImageView image = new ImageView(view.getContext());
                        image.setImageBitmap(bitmap);

                        AlertDialog.Builder builder =
                                new AlertDialog.Builder(view.getContext()).
                                        setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                            }
                                        }).
                                        setView(image);
                        builder.create().show();
                        break;

                    case R.id.action_delete:
                        i = new Intent(ctx, DeleteActivity.class);
                        i.putExtra(DeleteActivity.EXTRA_POSITION, position);
                        ctx.startActivity(i);
                        break;
                }

                return true;
            }
        });

        tl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TokenPersistence tp = new TokenPersistence(ctx);

                // Increment the token.
                Token token = tp.get(position);
                TokenCode codes = token.generateCodes();
                tp.save(token);

                // Copy code to clipboard.
                mClipMan.setPrimaryClip(ClipData.newPlainText(null, codes.getCurrentCode()));
                Toast.makeText(v.getContext().getApplicationContext(),
                        R.string.code_copied,
                        Toast.LENGTH_SHORT).show();

                mTokenCodes.put(token.getID(), codes);
                ((TokenLayout) v).start(token.getType(), codes, true);
            }
        });

        TokenCode tc = mTokenCodes.get(token.getID());
        if (tc != null && tc.getCurrentCode() != null)
            tl.start(token.getType(), tc, false);
    }

    @Override
    protected View createView(ViewGroup parent, int type) {
        return mLayoutInflater.inflate(R.layout.token, parent, false);
    }
}
