package com.mopub.nativeads;

import android.app.Activity;

import com.mopub.common.DownloadTask;
import com.mopub.common.GpsHelper;
import com.mopub.common.GpsHelperTest;
import com.mopub.common.SharedPreferencesHelper;
import com.mopub.common.util.test.support.ShadowAsyncTasks;
import com.mopub.common.util.test.support.TestMethodBuilderFactory;
import com.mopub.nativeads.test.support.SdkTestRunner;

import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.Semaphore;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.INTERNET;
import static com.mopub.common.util.Reflection.MethodBuilder;
import static com.mopub.nativeads.MoPubNative.MoPubNativeListener;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Robolectric.shadowOf;

@RunWith(SdkTestRunner.class)
@Config(shadows = {ShadowAsyncTasks.class})
public class MoPubNativeTest {
    private MoPubNative subject;
    private MethodBuilder methodBuilder;
    private Activity context;
    private MoPubNative.NativeGpsHelperListener nativeGpsHelperListener;
    private Semaphore semaphore;
    private static final String adUnitId = "test_adunit_id";
    private MoPubNativeListener moPubNativeListener;

    @Before
    public void setup() {
        context = new Activity();
        shadowOf(context).grantPermissions(ACCESS_NETWORK_STATE);
        shadowOf(context).grantPermissions(INTERNET);
        moPubNativeListener = mock(MoPubNativeListener.class);
        subject = new MoPubNative(context, adUnitId, moPubNativeListener);
        methodBuilder = TestMethodBuilderFactory.getSingletonMock();
        nativeGpsHelperListener = mock(MoPubNative.NativeGpsHelperListener.class);
        semaphore = new Semaphore(0);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                semaphore.release();
                return null;
            }
        }).when(nativeGpsHelperListener).onFetchAdInfoCompleted();
    }

    @After
    public void tearDown() throws Exception {
        reset(methodBuilder);
    }

    @Ignore("fix concurrency issues")
    @Test
    public void makeRequest_whenGooglePlayServicesIsLinkedAndAdInfoIsNotCached_shouldCacheAdInfoBeforeFetchingAd() throws Exception {
        SharedPreferencesHelper.getSharedPreferences(context).edit().clear().commit();
        GpsHelperTest.verifyCleanSharedPreferences(context);

        GpsHelper.setClassNamesForTesting();
        GpsHelperTest.TestAdInfo adInfo = new GpsHelperTest.TestAdInfo();

        when(methodBuilder.setStatic(any(Class.class))).thenReturn(methodBuilder);
        when(methodBuilder.addParam(any(Class.class), any())).thenReturn(methodBuilder);
        when(methodBuilder.execute()).thenReturn(
                GpsHelper.GOOGLE_PLAY_SUCCESS_CODE,
                adInfo,
                adInfo.ADVERTISING_ID,
                adInfo.LIMIT_AD_TRACKING_ENABLED
        );

        subject.makeRequest(nativeGpsHelperListener);
        semaphore.acquire();

        verify(nativeGpsHelperListener).onFetchAdInfoCompleted();
        GpsHelperTest.verifySharedPreferences(context, adInfo);
    }

    @Test
    public void makeRequest_whenGooglePlayServicesIsNotLinked_shouldFetchAdFast() throws Exception {
        SharedPreferencesHelper.getSharedPreferences(context).edit().clear().commit();
        GpsHelperTest.verifyCleanSharedPreferences(context);

        GpsHelper.setClassNamesForTesting();
        when(methodBuilder.setStatic(any(Class.class))).thenReturn(methodBuilder);
        when(methodBuilder.addParam(any(Class.class), any())).thenReturn(methodBuilder);

        // return error code so it fails
        when(methodBuilder.execute()).thenReturn(GpsHelper.GOOGLE_PLAY_SUCCESS_CODE + 1);

        subject.makeRequest(nativeGpsHelperListener);
        // no need to sleep since it run the callback without an async task

        verify(nativeGpsHelperListener).onFetchAdInfoCompleted();
        GpsHelperTest.verifyCleanSharedPreferences(context);
    }

    @Test
    public void makeRequest_whenGooglePlayServicesIsNotLinked_withNullContext_shouldReturnFast() throws Exception {
        subject.destroy();

        GpsHelper.setClassNamesForTesting();
        when(methodBuilder.setStatic(any(Class.class))).thenReturn(methodBuilder);
        when(methodBuilder.addParam(any(Class.class), any())).thenReturn(methodBuilder);

        // return error code so it fails
        when(methodBuilder.execute()).thenReturn(GpsHelper.GOOGLE_PLAY_SUCCESS_CODE + 1);

        subject.makeRequest(nativeGpsHelperListener);
        // no need to sleep since it run the callback without an async task

        verify(nativeGpsHelperListener, never()).onFetchAdInfoCompleted();
    }

    @Test
    public void makeRequest_whenGooglePlayServicesIsLinkedAndAdInfoIsCached_shouldFetchAdFast() throws Exception {
        GpsHelperTest.TestAdInfo adInfo = new GpsHelperTest.TestAdInfo();
        GpsHelperTest.populateAndVerifySharedPreferences(context, adInfo);
        GpsHelper.setClassNamesForTesting();

        when(methodBuilder.setStatic(any(Class.class))).thenReturn(methodBuilder);
        when(methodBuilder.addParam(any(Class.class), any())).thenReturn(methodBuilder);
        when(methodBuilder.execute()).thenReturn(
                GpsHelper.GOOGLE_PLAY_SUCCESS_CODE
        );

        subject.makeRequest(nativeGpsHelperListener);
        // no need to sleep since it run the callback without an async task

        verify(nativeGpsHelperListener).onFetchAdInfoCompleted();
        GpsHelperTest.verifySharedPreferences(context, adInfo);
    }

    @Test
    public void destroy_shouldSetMoPubNativeListenerToEmptyAndClearContext() throws Exception {
        assertThat(subject.getContextOrDestroy()).isSameAs(context);
        assertThat(subject.getMoPubNativeListener()).isSameAs(moPubNativeListener);

        subject.destroy();

        assertThat(subject.getContextOrDestroy()).isNull();
        assertThat(subject.getMoPubNativeListener()).isSameAs(MoPubNativeListener.EMPTY_MOPUB_NATIVE_LISTENER);
    }

    @Ignore("pending")
    @Test
    public void loadNativeAd_shouldQueueAsyncDownloadTask() throws Exception {
        Robolectric.getUiThreadScheduler().pause();

        subject.loadNativeAd(null);

        assertThat(Robolectric.getUiThreadScheduler().enqueuedTaskCount()).isEqualTo(1);
    }

    @Test
    public void loadNativeAd_shouldReturnFast() throws Exception {
        Robolectric.getUiThreadScheduler().pause();

        subject.destroy();
        subject.loadNativeAd(null);

        assertThat(Robolectric.getUiThreadScheduler().enqueuedTaskCount()).isEqualTo(0);
    }

    @Test
    public void requestNativeAd_withValidUrl_shouldStartDownloadTaskWithUrl() throws Exception {
        Robolectric.getUiThreadScheduler().pause();
        Robolectric.addPendingHttpResponse(200, "body");

        subject.requestNativeAd("http://www.mopub.com");

        verify(moPubNativeListener, never()).onNativeFail(any(NativeErrorCode.class));
        assertThat(wasDownloadTaskExecuted()).isTrue();

        List<?> latestParams = ShadowAsyncTasks.getLatestParams();
        assertThat(latestParams).hasSize(1);
        HttpGet httpGet = (HttpGet) latestParams.get(0);
        assertThat(httpGet.getURI().toString()).isEqualTo("http://www.mopub.com");
    }

    @Test
    public void requestNativeAd_withInvalidUrl_shouldFireNativeFailAndNotStartAsyncTask() throws Exception {
        Robolectric.getUiThreadScheduler().pause();

        subject.requestNativeAd("//\\//\\::::");

        verify(moPubNativeListener).onNativeFail(any(NativeErrorCode.class));
        assertThat(wasDownloadTaskExecuted()).isFalse();
    }

    @Test
    public void requestNativeAd_withNullUrl_shouldFireNativeFailAndNotStartAsyncTask() throws Exception {
        Robolectric.getUiThreadScheduler().pause();

        subject.requestNativeAd(null);

        verify(moPubNativeListener).onNativeFail(any(NativeErrorCode.class));
        assertThat(wasDownloadTaskExecuted()).isFalse();
    }

    private boolean wasDownloadTaskExecuted() {
        return ShadowAsyncTasks.wasCalled() &&
                (ShadowAsyncTasks.getLatestAsyncTask() instanceof DownloadTask);
    }
}
