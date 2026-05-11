package com.flipphoneguy.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class InfoActivity extends Activity {

    private Button btnUpdate;
    private TextView updateStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        ((TextView) findViewById(R.id.info_app_name)).setText(BuildConfig.APP_NAME);
        ((TextView) findViewById(R.id.info_version)).setText("v" + BuildConfig.VERSION_NAME);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        btnUpdate = (Button) findViewById(R.id.btn_check_update);
        updateStatus = (TextView) findViewById(R.id.update_status);
        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkForUpdate(); }
        });

        final String repoName = BuildConfig.REPO.contains("/")
            ? BuildConfig.REPO.split("/")[1] : BuildConfig.REPO;

        findViewById(R.id.btn_github_profile).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openUrl(BuildConfig.GITHUB_PROFILE);
            }
        });

        findViewById(R.id.btn_app_repo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openUrl(BuildConfig.GITHUB_PROFILE + "/" + repoName);
            }
        });
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void setStatus(final String text) {
        updateStatus.setVisibility(View.VISIBLE);
        updateStatus.setText(text);
    }

    private void checkForUpdate() {
        btnUpdate.setEnabled(false);
        setStatus(getString(R.string.update_checking));

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final ApkInstaller.LatestRelease release =
                        ApkInstaller.checkLatest(BuildConfig.REPO);

                    if (release == null || release.apkUrl == null) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                setStatus(getString(R.string.update_no_release));
                                btnUpdate.setEnabled(true);
                            }
                        });
                        return;
                    }

                    int cmp = ApkInstaller.compareVersions(
                        release.tag, BuildConfig.VERSION_NAME);

                    if (cmp <= 0) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                setStatus(getString(R.string.update_up_to_date,
                                    BuildConfig.VERSION_NAME));
                                btnUpdate.setEnabled(true);
                            }
                        });
                        return;
                    }

                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            setStatus(getString(R.string.update_downloading,
                                release.tag));
                        }
                    });

                    final File apk = ApkInstaller.download(
                        InfoActivity.this, release.apkUrl);

                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            showInstallDialog(apk);
                        }
                    });

                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            setStatus(getString(R.string.update_download_failed,
                                e.getMessage()));
                            btnUpdate.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void showInstallDialog(final File apk) {
        setStatus(getString(R.string.update_ready));
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_ready))
            .setItems(new String[]{
                getString(R.string.update_install_root),
                getString(R.string.update_install_system)
            }, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) installWithRoot(apk);
                    else installWithSystem(apk);
                }
            })
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override public void onCancel(DialogInterface d) {
                    btnUpdate.setEnabled(true);
                }
            })
            .show();
    }

    private void installWithRoot(final File apk) {
        setStatus(getString(R.string.update_installing));
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ApkInstaller.installRoot(apk);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            setStatus("Updated successfully");
                            Toast.makeText(InfoActivity.this,
                                "Updated", Toast.LENGTH_SHORT).show();
                            btnUpdate.setEnabled(true);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            setStatus(getString(R.string.update_install_failed,
                                e.getMessage()));
                            btnUpdate.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void installWithSystem(File apk) {
        try {
            ApkInstaller.installSystem(InfoActivity.this, apk);
        } catch (Exception e) {
            setStatus(getString(R.string.update_install_failed, e.getMessage()));
        }
        btnUpdate.setEnabled(true);
    }
}
