project (TeamTalkClients)

option (BUILD_TEAMTALK_CLIENT_PRISM "Build TeamTalk clients with Prism screen reader support" ON)
if (BUILD_TEAMTALK_CLIENT_PRISM)
  add_subdirectory (Prism)
endif()

if (MSVC)
  add_subdirectory (TeamTalkClassic)
endif()

add_subdirectory (jSpamBot)
add_subdirectory (jTeamTalk)
add_subdirectory (qtTeamTalk)
add_subdirectory (ttserverlog)
