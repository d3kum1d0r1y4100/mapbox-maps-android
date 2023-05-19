package com.mapbox.maps

import com.mapbox.verifyNo
import com.mapbox.verifyOnce
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class StyleObserverTest {

  private lateinit var mainStyleLoadedListener: Style.OnStyleLoaded
  private lateinit var styleObserver: StyleObserver

  @Before
  fun setUp() {
    mockkStatic("com.mapbox.maps.MapboxLogger")
    every { logE(any(), any()) } just Runs

    mainStyleLoadedListener = mockk(relaxed = true)
    styleObserver = StyleObserver(
      styleManager = mockk(relaxUnitFun = true),
      styleLoadedListener = mainStyleLoadedListener,
      nativeObserver = mockk(relaxUnitFun = true),
      pixelRatio = 1.0f
    )
  }

  @After
  fun cleanUp() {
    unmockkStatic("com.mapbox.maps.MapboxLogger")
  }

  /**
   * Verifies if the correct listeners are attached to NativeMapObserver when StyleObserver is created
   */
  @Test
  fun onStyleObserverCreate() {
    val nativeObserver = mockk<NativeObserver>(relaxed = true)
    StyleObserver(mockk(), mockk(relaxed = true), nativeObserver, 1.0f)
    verify { nativeObserver.addOnStyleLoadedListener(any()) }
    verify { nativeObserver.addOnMapLoadErrorListener(any()) }
    verify { nativeObserver.addOnStyleDataLoadedListener(any()) }
  }

  /**
   * Verifies if the correct listeners are detached to NativeMapObserver when StyleObserver is destroyed
   */
  @Test
  fun onStyleObserverDestroy() {
    val nativeObserver = mockk<NativeObserver>(relaxed = true)
    StyleObserver(mockk(), mockk(relaxed = true), nativeObserver, 1.0f).onDestroy()
    verify { nativeObserver.removeOnStyleLoadedListener(any()) }
    verify { nativeObserver.removeOnMapLoadErrorListener(any()) }
    verify { nativeObserver.removeOnStyleDataLoadedListener(any()) }
  }

  /**
   * Verifies if the user provided OnStyleLoaded is called when style loading finishes.
   * Additionally verify we don't apply transition options if they were not specified.
   */
  @Test
  fun onStyleLoadSuccess() {
    val styleLoaded = mockk<Style.OnStyleLoaded>(relaxed = true)
    styleObserver.setLoadStyleListener(styleLoaded, mockk(relaxed = true), null, null, null)
    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.STYLE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    ) // needed to initialize style internally
    styleObserver.run(mockk<StyleLoaded>())
    verify { styleLoaded.onStyleLoaded(any()) }
    verify { mainStyleLoadedListener.onStyleLoaded(any()) }
  }

  /**
   * Verifies if the multiple user provided OnStyleLoaded are called when style loading finishes
   */
  @Test
  fun onStyleLoadSuccessMulti() {
    val userLoadStyleListener = mockk<Style.OnStyleLoaded>(relaxed = true)
    styleObserver.setLoadStyleListener(
      userLoadStyleListener,
      mockk(relaxed = true),
      null,
      null,
      null
    )
    val getStyleListener = mockk<Style.OnStyleLoaded>(relaxed = true)
    styleObserver.addGetStyleListener(getStyleListener)
    val getStyleListener2 = mockk<Style.OnStyleLoaded>(relaxed = true)
    styleObserver.addGetStyleListener(getStyleListener2)
    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.STYLE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    ) // needed to initialize style internally
    styleObserver.run(mockk<StyleLoaded>())
    verify { userLoadStyleListener.onStyleLoaded(any()) }
    verify { getStyleListener.onStyleLoaded(any()) }
    verify { getStyleListener2.onStyleLoaded(any()) }
  }

  /**
   * Verifies that loadStyle callback is overwritten with the consecutive call
   */
  @Test
  fun onStyleLoadedOverwritten() {
    val styleLoadedFail = mockk<Style.OnStyleLoaded>(relaxed = true)
    styleObserver.setLoadStyleListener(styleLoadedFail, mockk(relaxed = true), null, null, null)
    val styleLoadedSuccess = mockk<Style.OnStyleLoaded>(relaxed = true)
    styleObserver.setLoadStyleListener(styleLoadedSuccess, mockk(relaxed = true), null, null, null)
    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.STYLE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    ) // needed to initialize style internally
    styleObserver.run(mockk<StyleLoaded>())
    verifyNo { styleLoadedFail.onStyleLoaded(any()) }
    verify { styleLoadedSuccess.onStyleLoaded(any()) }
  }

  /**
   * Verifies if the user provided MapLoadingErrorCallback is called when the style has produced an error
   */
  @Test
  fun onStyleLoadError() {
    val errorListener = mockk<MapLoadingErrorCallback>(relaxed = true)
    styleObserver.setLoadStyleListener(null, mockk(relaxed = true), null, null, errorListener)
    styleObserver.run(mockk<MapLoadingError>(relaxed = true))
    verify { errorListener.run(any()) }
  }

  /**
   * Verifies that loadStyle error callback is overwritten with the consecutive call
   */
  @Test
  fun onStyleLoadErrorNotCalled() {
    val errorListenerFail = mockk<MapLoadingErrorCallback>(relaxed = true)
    styleObserver.setLoadStyleListener(null, mockk(relaxed = true), null, null, errorListenerFail)
    val errorListenerSuccess = mockk<MapLoadingErrorCallback>(relaxed = true)
    styleObserver.setLoadStyleListener(
      null,
      mockk(relaxed = true),
      null,
      null,
      errorListenerSuccess
    )
    styleObserver.run(mockk<MapLoadingError>(relaxed = true))
    verifyNo { errorListenerFail.run(any()) }
    verify { errorListenerSuccess.run(any()) }
  }

  @Test
  fun onStyleDataLoadedNotifiesMapboxMap() {
    val styleCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleSpritesCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleSourcesCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleUserCallback = mockk<Style.OnStyleLoaded>(relaxed = true)

    styleObserver.setLoadStyleListener(
      styleUserCallback,
      styleCallback,
      styleSpritesCallback,
      styleSourcesCallback,
      null
    )

    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.STYLE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )

    verify { styleCallback.onStyleLoaded(any()) }
    verifyNo { styleSpritesCallback.onStyleLoaded(any()) }
    verifyNo { styleSourcesCallback.onStyleLoaded(any()) }

    verifyNo { styleSpritesCallback.onStyleLoaded(any()) }
    verifyNo { mainStyleLoadedListener.onStyleLoaded(any()) }
  }

  @Test
  fun onStyleDataSpritesLoadedNotifiesMapboxMap() {
    val styleCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleSpritesCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleSourcesCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleUserCallback = mockk<Style.OnStyleLoaded>(relaxed = true)

    styleObserver.setLoadStyleListener(
      styleUserCallback,
      styleCallback,
      styleSpritesCallback,
      styleSourcesCallback,
      null
    )

    // STYLE event arrives first and initializes Style object internally
    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.STYLE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )

    verify { styleCallback.onStyleLoaded(any()) }
    verifyNo { styleSpritesCallback.onStyleLoaded(any()) }

    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.SPRITE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )

    verify { styleSpritesCallback.onStyleLoaded(any()) }
    verifyNo { styleSourcesCallback.onStyleLoaded(any()) }

    verifyNo { styleUserCallback.onStyleLoaded(any()) }
    verifyNo { mainStyleLoadedListener.onStyleLoaded(any()) }
  }

  @Test
  fun onStyleDataSourcesLoadedNotifiesMapboxMap() {
    val styleCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleSpritesCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleSourcesCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleUserCallback = mockk<Style.OnStyleLoaded>(relaxed = true)

    styleObserver.setLoadStyleListener(
      styleUserCallback,
      styleCallback,
      styleSpritesCallback,
      styleSourcesCallback,
      null
    )

    // STYLE event arrives first and initializes Style object internally
    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.STYLE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )

    verifyNo { styleSourcesCallback.onStyleLoaded(any()) }

    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.SOURCES,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )

    verify { styleSourcesCallback.onStyleLoaded(any()) }
    verify { styleCallback.onStyleLoaded(any()) }
    verifyNo { styleSpritesCallback.onStyleLoaded(any()) }

    verifyNo { styleSpritesCallback.onStyleLoaded(any()) }
    verifyNo { mainStyleLoadedListener.onStyleLoaded(any()) }
  }

  @Test
  fun onStyleDataOverwritten() {
    val styleNotCalled = mockk<Style.OnStyleLoaded>(relaxed = true)
    val spritesNotCalled = mockk<Style.OnStyleLoaded>(relaxed = true)
    val sourcesNotCalled = mockk<Style.OnStyleLoaded>(relaxed = true)

    val styleCalled = mockk<Style.OnStyleLoaded>(relaxed = true)
    val spritesCalled = mockk<Style.OnStyleLoaded>(relaxed = true)
    val sourcesCalled = mockk<Style.OnStyleLoaded>(relaxed = true)

    styleObserver.setLoadStyleListener(
      null,
      styleNotCalled,
      spritesNotCalled,
      sourcesNotCalled,
      null
    )
    styleObserver.setLoadStyleListener(null, styleCalled, spritesCalled, sourcesCalled, null)

    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.STYLE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )
    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.SOURCES,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )
    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.SPRITE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )
    verifyNo { styleNotCalled.onStyleLoaded(any()) }
    verifyNo { spritesNotCalled.onStyleLoaded(any()) }
    verifyNo { sourcesNotCalled.onStyleLoaded(any()) }

    verify { styleCalled.onStyleLoaded(any()) }
    verify { spritesCalled.onStyleLoaded(any()) }
    verify { sourcesCalled.onStyleLoaded(any()) }
  }

  @Test
  fun onStyleLoadedThrowsIfNoStyleData() {
    val styleCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleSpritesCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleSourcesCallback = mockk<Style.OnStyleLoaded>(relaxed = true)

    styleObserver.setLoadStyleListener(
      null,
      styleCallback,
      styleSpritesCallback,
      styleSourcesCallback,
      null
    )

    assertThrows(MapboxMapException::class.java) {
      styleObserver.run(mockk<StyleLoaded>())
    }
  }

  @Test
  fun onStyleLoadedCallsSpritesListenerIfNoStyleDataSpritesLoaded() {
    val styleCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleSpritesCallback = mockk<Style.OnStyleLoaded>(relaxed = true)

    styleObserver.setLoadStyleListener(
      null,
      styleCallback,
      styleSpritesCallback,
      null,
      null
    )

    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.STYLE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )
    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.SOURCES,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )

    verifyNo { styleSpritesCallback.onStyleLoaded(any()) }

    styleObserver.run(mockk<StyleLoaded>())

    verify { styleSpritesCallback.onStyleLoaded(any()) }
  }

  @Test
  fun onStyleLoadedCallsSourcesListenerIfNoStyleDataSourcesLoaded() {
    val styleCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleSourcesCallback = mockk<Style.OnStyleLoaded>(relaxed = true)

    styleObserver.setLoadStyleListener(
      null,
      styleCallback,
      null,
      styleSourcesCallback,
      null
    )

    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.STYLE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )
    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.SPRITE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )

    verifyNo { styleSourcesCallback.onStyleLoaded(any()) }

    styleObserver.run(mockk<StyleLoaded>())

    verify { styleSourcesCallback.onStyleLoaded(any()) }
  }

  @Test
  fun `StyleDataType - Sources and Sprites events are ignored until StyleDataType - Style is not received on style change`() {
    val styleCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleSourcesCallback = mockk<Style.OnStyleLoaded>(relaxed = true)
    val styleSpritesCallback = mockk<Style.OnStyleLoaded>(relaxed = true)

    styleObserver.setLoadStyleListener(
      null,
      styleCallback,
      styleSpritesCallback,
      styleSourcesCallback,
      null
    )

    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.SOURCES,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )
    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.SPRITE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )

    verifyNo { styleSourcesCallback.onStyleLoaded(any()) }
    verifyNo { styleSpritesCallback.onStyleLoaded(any()) }

    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.STYLE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )
    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.SOURCES,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )
    styleObserver.run(
      StyleDataLoaded(
        StyleDataLoadedType.SPRITE,
        EventTimeInterval(
          Date(0), Date(0)
        )
      )
    )

    verifyOnce { styleCallback.onStyleLoaded(any()) }
    verifyOnce { styleSourcesCallback.onStyleLoaded(any()) }
    verifyOnce { styleSpritesCallback.onStyleLoaded(any()) }
  }
}