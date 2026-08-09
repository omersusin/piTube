package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val subscriptionRepo = remember { SubscriptionRepository.getInstance(context) }

    var currentStep by remember { mutableStateOf(OnboardingStep.INTERESTS) }

    var selectedTopics by remember { mutableStateOf<Set<String>>(emptySet()) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ChannelSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var subscribedInSession by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun finish() {
        scope.launch {
            FlowNeuroEngine.completeOnboarding(context, selectedTopics)
            onComplete()
        }
    }

    fun advance() {
        val next = OnboardingStep.entries.getOrNull(currentStep.index + 1)
        if (next != null) currentStep = next else finish()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { StepIndicatorBar(currentStep = currentStep) },
        bottomBar = {
            OnboardingBottomBar(
                isFirstStep = currentStep == OnboardingStep.INTERESTS,
                isLastStep = currentStep == OnboardingStep.CHANNELS,
                canAdvance = when (currentStep) {
                    OnboardingStep.INTERESTS -> selectedTopics.size >= MIN_TOPICS
                    else -> true
                },
                onBack = {
                    OnboardingStep.entries.getOrNull(currentStep.index - 1)?.let { currentStep = it }
                },
                onNext = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    advance()
                },
                onSkip = { advance() }
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.index > initialState.index
                val enter = if (forward)
                    slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(250))
                else
                    slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(250))
                val exit = if (forward)
                    slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(200))
                else
                    slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(200))
                enter togetherWith exit
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "step_content"
        ) { step ->
            when (step) {
                OnboardingStep.INTERESTS -> InterestsStep(
                    selectedTopics = selectedTopics,
                    onTopicToggle = { topic ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedTopics = if (selectedTopics.contains(topic))
                            selectedTopics - topic
                        else
                            selectedTopics + topic
                    }
                )
                OnboardingStep.CHANNELS -> ChannelsStep(
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    subscribedInSession = subscribedInSession,
                    onQueryChange = { q ->
                        searchQuery = q
                        searchJob?.cancel()
                        if (q.isBlank()) {
                            searchResults = emptyList()
                            isSearching = false
                            return@ChannelsStep
                        }
                        searchJob = scope.launch {
                            delay(400)
                            isSearching = true
                            searchResults = searchChannels(q)
                            isSearching = false
                        }
                    },
                    onSubscribeToggle = { result ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                            if (subscribedInSession.contains(result.channelId)) {
                                subscriptionRepo.unsubscribe(result.channelId)
                                subscribedInSession = subscribedInSession - result.channelId
                            } else {
                                subscriptionRepo.subscribe(
                                    ChannelSubscription(
                                        channelId = result.channelId,
                                        channelName = result.name,
                                        channelThumbnail = result.thumbnailUrl,
                                        subscribedAt = System.currentTimeMillis()
                                    )
                                )
                                subscribedInSession = subscribedInSession + result.channelId
                            }
                        }
                    }
                )
            }
        }
    }
}
